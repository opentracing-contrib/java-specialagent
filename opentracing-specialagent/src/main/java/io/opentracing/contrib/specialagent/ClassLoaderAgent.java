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

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentracing.contrib.specialagent.BootLoaderAgent.Mutex;
import io.opentracing.contrib.specialagent.SpecialAgent.AllPluginsClassLoader;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
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

/**
 * The {@code ClassLoaderAgent} instruments the {@code ClassLoader} class to
 * achieve direct loading of bytecode into target class loaders, which is
 * necessary for the SpecialAgent to be able to load instrumentation classes
 * into class loaders.
 *
 * @author Seva Safris
 */
public class ClassLoaderAgent {
  private static final Logger logger = Logger.getLogger(ClassLoaderAgent.class.getName());
  public static final ClassFileLocator locatorProxy = BootLoaderAgent.cachedLocator;

  public static void premain(final Instrumentation inst) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("\n<<<<<<<<<<<<<<<<< Installing ClassLoaderAgent >>>>>>>>>>>>>>>>>>\n");

    final Narrowable builder = new AgentBuilder.Default()
      .ignore(none())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(isSubTypeOf(ClassLoader.class).and(not(is(RuleClassLoader.class)).and(not(is(AllPluginsClassLoader.class)))));

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          final Advice advice = locatorProxy != null ? Advice.to(FindClass.class, locatorProxy) : Advice.to(FindClass.class);
          return builder.visit(advice.on(named("findClass").and(returns(Class.class).and(takesArguments(String.class)))));
        }})
      .installOn(inst);

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          final Advice advice = locatorProxy != null ? Advice.to(FindResource.class, locatorProxy) : Advice.to(FindClass.class);
          return builder.visit(advice.on(named("findResource").and(returns(URL.class).and(takesArguments(String.class)))));
        }})
      .installOn(inst);

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          final Advice advice = locatorProxy != null ? Advice.to(FindResources.class, locatorProxy) : Advice.to(FindClass.class);
          return builder.visit(advice.on(named("findResources").and(returns(Enumeration.class).and(takesArguments(String.class)))));
        }})
      .installOn(inst);
  }

  public static class FindClass {
    public static final Mutex mutex = new Mutex();

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = ClassNotFoundException.class)
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Class<?> returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) ClassNotFoundException thrown) {
      if (returned != null || !mutex.get().add(arg))
        return;

      try {
        final byte[] bytecode = SpecialAgent.findClass(thiz, arg);
        if (bytecode == null)
          return;

        if (AgentRule.logger.isLoggable(Level.FINEST))
          AgentRule.logger.finest("<<<<<<<< defineClass(\"" + arg + "\")");

        final Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
        returned = (Class<?>)defineClass.invoke(thiz, arg, bytecode, 0, bytecode.length, null);
        thrown = null;
      }
      catch (final Throwable t) {
        AgentRule.logger.log(Level.SEVERE, "<><><><> ClassLoaderAgent.FindClass#exit", t);
      }
      finally {
        mutex.get().remove(arg);
      }
    }
  }

  public static class FindResource {
    public static final Mutex mutex = new Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) URL returned) {
      if (returned != null || !mutex.get().add(arg))
        return;

      try {
        final URL resource = SpecialAgent.findResource(thiz, arg);
        if (resource != null)
          returned = resource;
      }
      catch (final Throwable t) {
        AgentRule.logger.log(Level.SEVERE, "<><><><> ClassLoaderAgent.FindResource#exit", t);
      }
      finally {
        mutex.get().remove(arg);
      }
    }
  }

  public static class FindResources {
    public static final Mutex mutex = new Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Enumeration<URL> returned) {
      if (!mutex.get().add(arg))
        return;

      try {
        final Enumeration<URL> resources = SpecialAgent.findResources(thiz, arg);
        if (resources == null)
          return;

        returned = returned == null ? resources : new CompoundEnumeration<>(returned, resources);
      }
      catch (final Throwable t) {
        AgentRule.logger.log(Level.SEVERE, "<><><><> ClassLoaderAgent.FindResources#exit", t);
      }
      finally {
        mutex.get().remove(arg);
      }
    }
  }
}