/* Copyright 2018 The OpenTracing Authors
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

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.reflect.Method;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

/**
 * The {@code ClassLoaderAgent} instruments the {@code ClassLoader} class to
 * achieve direct loading of bytecode into target class loaders, which is
 * necessary for the SpecialAgent to be able to load instrumentation classes
 * into class loaders.
 *
 * @author Seva Safris
 */
public class ClassLoaderAgentRule extends DefaultAgentRule {
  public static final ClassFileLocator locatorProxy = BootLoaderAgent.cachedLocator;

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    log("\n<<<<<<<<<<<<<<<<< Installing ClassLoaderAgent >>>>>>>>>>>>>>>>>>\n", null, DefaultLevel.FINE);

//    final Narrowable narrowable = builder.type(isSubTypeOf(ClassLoader.class).and(not(nameStartsWith(RuleClassLoader.class.getName()))).and(not(nameStartsWith(PluginsClassLoader.class.getName()))));
    final List<Extendable> builders = Arrays.asList(
      builder.type(isSubTypeOf(ClassLoader.class)).transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder
            .visit((locatorProxy != null ? Advice.to(DefineClass.class, locatorProxy) : Advice.to(DefineClass.class)).on(named("defineClass").and(returns(Class.class).and(takesArgument(0, String.class)))))
            .visit((locatorProxy != null ? Advice.to(LoadClass.class, locatorProxy) : Advice.to(LoadClass.class)).on(named("loadClass").and(returns(Class.class).and(takesArguments(String.class)))))
            .visit((locatorProxy != null ? Advice.to(FindResource.class, locatorProxy) : Advice.to(FindResource.class)).on(named("findResource").and(returns(URL.class).and(takesArguments(String.class)))))
            .visit((locatorProxy != null ? Advice.to(FindResources.class, locatorProxy) : Advice.to(FindResources.class)).on(named("findResources").and(returns(Enumeration.class).and(takesArguments(String.class)))));
        }}));

    log("\n>>>>>>>>>>>>>>>>>> Installed ClassLoaderAgent <<<<<<<<<<<<<<<<<<\n", null, DefaultLevel.FINE);
    return builders;
  }

  public static boolean isExcluded(final ClassLoader thiz) {
    final String className = thiz.getClass().getName();
    return className.startsWith("io.opentracing.contrib.specialagent.RuleClassLoader") || className.startsWith("io.opentracing.contrib.specialagent.PluginsClassLoader") || className.startsWith("io.opentracing.contrib.specialagent.AgentRunnerClassLoader") || className.startsWith("io.opentracing.contrib.specialagent.IsoClassLoader");
  }

  public static class DefineClass {
    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz) {
      if (!isExcluded(thiz))
        SpecialAgent.inject(thiz);
    }
  }

  public static class LoadClass {
    public static final BootLoaderAgent.Mutex mutex = new BootLoaderAgent.Mutex();
    public static Method defineClass;

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = ClassNotFoundException.class)
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Class<?> returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) ClassNotFoundException thrown) {
      if (returned != null || isExcluded(thiz))
        return;

      final Set<String> visited;
      if (!(visited = mutex.get()).add(name))
        return;

      try {
        final Class<?> bootstrapClass = BootProxyClassLoader.INSTANCE.loadClassOrNull(name, false);
        if (bootstrapClass != null) {
          log(">>>>>>>> BootLoader#loadClassOrNull(\"" + name + "\"): " + bootstrapClass, null, DefaultLevel.FINEST);

          returned = bootstrapClass;
          thrown = null;
          return;
        }

//        if (SpecialAgent.isoClassLoader != null && name.startsWith("io.opentracing.")) {
//          final Class<?> isoClass = SpecialAgent.isoClassLoader.loadClassOrNull(name);
//          if (isoClass != null) {
//            returned = isoClass;
//            thrown = null;
//            return;
//          }
//        }

        final byte[] bytecode = SpecialAgent.findClass(thiz, name);
        if (bytecode == null)
          return;

        log("<<<<<<<< defineClass(\"" + name + "\")", null, DefaultLevel.FINEST);

        if (defineClass == null)
          defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);

        returned = (Class<?>)defineClass.invoke(thiz, name, bytecode, 0, bytecode.length, null);
        thrown = null;
      }
      catch (final Throwable t) {
        log("<><><><> ClassLoaderAgent.LoadClass#exit(\"" + name + "\")", t, DefaultLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }

  public static class FindResource {
    public static final BootLoaderAgent.Mutex mutex = new BootLoaderAgent.Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) URL returned) {
      if (returned != null || isExcluded(thiz))
        return;

      final Set<String> visited;
      if (!(visited = mutex.get()).add(name))
        return;

      try {
        final URL resource = SpecialAgent.findResource(thiz, name);
        if (resource != null) {
          returned = resource;
          return;
        }

//        if (SpecialAgent.isoClassLoader != null && name.startsWith("io.opentracing.")) {
//          final URL isoResource = SpecialAgent.isoClassLoader.getResource(name);
//          if (isoResource != null) {
//            returned = isoResource;
//            return;
//          }
//        }
      }
      catch (final Throwable t) {
        log("<><><><> ClassLoaderAgent.FindResource#exit", t, DefaultLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }

  public static class FindResources {
    public static final BootLoaderAgent.Mutex mutex = new BootLoaderAgent.Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Enumeration<URL> returned) {
      if (isExcluded(thiz))
        return;

      final Set<String> visited;
      if (!(visited = mutex.get()).add(name))
        return;

      try {
        final Enumeration<URL> resources = SpecialAgent.findResources(thiz, name);
        if (resources != null)
          returned = returned == null ? resources : new CompoundEnumeration<>(returned, resources);

//        if (SpecialAgent.isoClassLoader != null && name.startsWith("io.opentracing.")) {
//          final Enumeration<URL> isoResources = SpecialAgent.isoClassLoader.getResources(name);
//          if (isoResources != null)
//            returned = returned == null ? isoResources : new CompoundEnumeration<>(returned, isoResources);
//        }
      }
      catch (final Throwable t) {
        log("<><><><> ClassLoaderAgent.FindResources#exit", t, DefaultLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }
}