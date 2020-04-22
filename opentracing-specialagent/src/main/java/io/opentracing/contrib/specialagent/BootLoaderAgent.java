/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import static io.opentracing.contrib.specialagent.DefaultAgentRule.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.opentracing.contrib.specialagent.DefaultAgentRule.DefaultLevel;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class BootLoaderAgent {
//  public static final Logger logger = Logger.getLogger(BootLoaderAgent.class);
  public static final CachedClassFileLocator cachedLocator;
  public static final List<JarFile> jarFiles = new ArrayList<>();
  private static boolean loaded = false;

  static {
    try {
      cachedLocator = new CachedClassFileLocator(ClassFileLocator.ForClassLoader.ofSystemLoader(),
        // BootLoaderAgent @Advice classes
        FindBootstrapResource.class, FindBootstrapResources.class, AppendToBootstrap.class,
        // ClassLoaderAgent @Advice classes (only necessary for ClassLoaderAgentTest)
        ClassLoaderAgent.DefineClass.class, ClassLoaderAgent.LoadClass.class, ClassLoaderAgent.FindResource.class, ClassLoaderAgent.FindResources.class,
        // SpecialAgentAgent @Advice classes (only necessary for ClassLoaderAgentTest)
        SpecialAgentAgent.FindClass.class, SpecialAgentAgent.FindResource.class, SpecialAgentAgent.FindResources.class);
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static boolean hasClassMethod(final String className, final String methodName, final Class<?> ... parameterTypes) {
    try {
      final Class<?> cls = Class.forName(className);
      return hasMethod(cls, methodName, parameterTypes);
    }
    catch (final ClassNotFoundException e) {
      return false;
    }
  }

  private static boolean hasMethod(final Class<?> cls, final String methodName, final Class<?> ... parameterTypes) {
    try {
      cls.getDeclaredMethod(methodName, parameterTypes);
      return true;
    }
    catch (final NoSuchMethodException e) {
      return false;
    }
  }

  public static void premain(final Instrumentation inst, final JarFile ... jarFiles) {
    if (loaded)
      return;

    if (jarFiles != null)
      for (final JarFile jarFile : jarFiles)
        if (jarFile != null)
          BootLoaderAgent.jarFiles.add(jarFile);

    AgentBuilder builder = new AgentBuilder.Default()
      .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("sun.reflect.")).or(isSynthetic()), any(), any())
      .disableClassFormatChanges()
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE);

    builder = builder.type(isSubTypeOf(Instrumentation.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AppendToBootstrap.class, cachedLocator).on(named("appendToBootstrapClassLoaderSearch").and(takesArguments(1).and(takesArgument(0, JarFile.class)))));
        }});

    // jdk1.[78]
    if (hasMethod(ClassLoader.class, "getBootstrapResource", String.class)) {
      builder = builder.type(isSubTypeOf(ClassLoader.class))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(Advice.to(FindBootstrapResource.class, cachedLocator).on(isStatic().and(named("getBootstrapResource").and(returns(URL.class).and(takesArguments(1).and(takesArgument(0, String.class)))))));
          }})
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(Advice.to(FindBootstrapResources.class, cachedLocator).on(isStatic().and(named("getBootstrapResources").and(returns(Enumeration.class).and(takesArguments(1).and(takesArgument(0, String.class)))))));
          }});
    }

    // jdk9+
    if (hasClassMethod("jdk.internal.loader.BuiltinClassLoader", "findResource", String.class)) {
      builder = builder.type(hasSuperType(named("jdk.internal.loader.BuiltinClassLoader")))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(Advice.to(FindBootstrapResource.class, cachedLocator).on(named("findResource").and(returns(URL.class).and(takesArguments(1).and(takesArgument(0, String.class))))));
          }})
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(Advice.to(FindBootstrapResources.class, cachedLocator).on(named("findResources").and(returns(Enumeration.class).and(takesArguments(1).and(takesArgument(0, String.class))))));
          }});
    }

    builder.installOn(inst);
    loaded = true;
  }

  public static class Mutex extends ThreadLocal<Set<String>> {
    @Override
    protected Set<String> initialValue() {
      return new HashSet<>();
    }
  }

  public static class FindBootstrapResource {
    public static final Mutex mutex = new Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) URL returned) {
      if (returned != null || jarFiles.size() == 0)
        return;

      final Set<String> visited;
      if (!(visited = mutex.get()).add(name))
        return;

      try {
        URL resource = null;
        for (final JarFile jarFile : jarFiles) {
          final JarEntry entry = jarFile.getJarEntry(name);
          if (entry != null) {
            try {
              resource = new URL("jar:file:" + jarFile.getName() + "!/" + name);
              break;
            }
            catch (final MalformedURLException e) {
              throw new UnsupportedOperationException(e);
            }
          }
        }

        if (resource != null) {
          returned = resource;
          return;
        }
      }
      catch (final Throwable t) {
        log("<><><><> BootLoaderAgent.FindBootstrapResource#exit", t, DefaultLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }

  public static class FindBootstrapResources {
    public static final Mutex mutex = new Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Enumeration<URL> returned) {
      if (jarFiles.size() == 0)
        return;

      final Set<String> visited = mutex.get();
      if (!visited.add(name))
        return;

      try {
        final List<URL> resources = new ArrayList<>();
        for (final JarFile jarFile : jarFiles) {
          final JarEntry entry = jarFile.getJarEntry(name);
          if (entry == null)
            continue;

          try {
            resources.add(new URL("jar:file:" + jarFile.getName() + "!/" + name));
          }
          catch (final MalformedURLException e) {
            throw new UnsupportedOperationException(e);
          }
        }

        if (resources.size() != 0) {
          final Enumeration<URL> enumeration = Collections.enumeration(resources);
          returned = returned == null ? enumeration : new CompoundEnumeration<>(returned, enumeration);
        }
      }
      catch (final Throwable t) {
        log("<><><><> BootLoaderAgent.FindBootstrapResources#exit", t, DefaultLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }

  public static class AppendToBootstrap {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Argument(0) JarFile arg) {
      try {
        jarFiles.add(arg);
      }
      catch (final Throwable t) {
        log("<><><><> BootLoaderAgent.AppendToBootstrap#exit", t, DefaultLevel.SEVERE);
      }
    }
  }
}