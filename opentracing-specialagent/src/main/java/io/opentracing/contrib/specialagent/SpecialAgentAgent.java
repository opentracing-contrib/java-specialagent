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

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.Enumeration;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

/**
 * ByteBuddy agent that intercepts the
 * {@link SpecialAgent#findClass(ClassLoader,String)} method to override its returned
 * value.
 * <p>
 * This class is used for testing of {@link ClassLoaderAgentRule}.
 *
 * @author Seva Safris
 */
public class SpecialAgentAgent {
  /**
   * Entrypoint to load the {@code SpecialAgentAgent}.
   *
   * @param agentArgs Agent arguments.
   * @param inst The {@code Instrumentation}.
   * @throws Exception If an error has occurred.
   */
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    final Narrowable builder = new AgentBuilder.Default()
      .ignore(none())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(is(SpecialAgent.class));

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(FindClass.class, BootLoaderAgent.cachedLocator).on(isStatic().and(named("findClass").and(returns(byte[].class).and(takesArguments(ClassLoader.class, String.class))))));
        }})
      .installOn(inst);

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(FindResource.class, BootLoaderAgent.cachedLocator).on(isStatic().and(named("findResource").and(returns(URL.class).and(takesArguments(ClassLoader.class, String.class))))));
        }})
      .installOn(inst);

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(FindResources.class, BootLoaderAgent.cachedLocator).on(isStatic().and(named("findResources").and(returns(Enumeration.class).and(takesArguments(ClassLoader.class, String.class))))));
        }})
      .installOn(inst);
  }

  public static class FindClass {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Argument(0) ClassLoader classLoader, final @Advice.Argument(1) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) byte[] returned) {
      try {
        String classpath = System.getProperty("java.class.path");
        final int index = classpath.indexOf("/opentracing-api-");
        final int start = classpath.lastIndexOf(File.pathSeparatorChar, index);
        final int end = classpath.indexOf(File.pathSeparatorChar, index);
        classpath = classpath.substring(start + 1, end != -1 ? end : classpath.length());
        if (!classpath.endsWith(".jar") && !classpath.endsWith("/"))
          classpath += "/";

        try (final RuleClassLoader ruleClassLoader = new RuleClassLoader(new URL[] {new URL("file", null, classpath)}, null)) {
          final URL resource = ruleClassLoader.findResource(arg.replace('.', '/').concat(".class"));
          if (resource != null)
            returned = AssembleUtil.readBytes(resource);
        }

        if (AgentRule.logger.isLoggable(Level.FINEST))
          AgentRule.logger.finest("<<<<<<< Agent#findClass(" + (classLoader == null ? "null" : classLoader.getClass().getName() + "@" + Integer.toString(System.identityHashCode(classLoader), 16)) + "," + arg + "): " + returned);
      }
      catch (final Throwable t) {
        AgentRule.logger.log(Level.SEVERE, "<><><><> AgentAgent.FindClass#exit", t);
      }
    }
  }

  public static class FindResource {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Argument(0) ClassLoader classLoader, final @Advice.Argument(1) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) URL returned) {
      try {
        String classpath = System.getProperty("java.class.path");
        final int index = classpath.indexOf("/opentracing-api-");
        final int start = classpath.lastIndexOf(File.pathSeparatorChar, index);
        final int end = classpath.indexOf(File.pathSeparatorChar, index);
        classpath = classpath.substring(start + 1, end != -1 ? end : classpath.length());
        if (!classpath.endsWith(".jar") && !classpath.endsWith("/"))
          classpath += "/";

        try (final RuleClassLoader ruleClassLoader = new RuleClassLoader(new URL[] {new URL("file", null, classpath)}, null)) {
          returned = ruleClassLoader.findResource(arg);
        }

        if (AgentRule.logger.isLoggable(Level.FINEST))
          AgentRule.logger.finest("<<<<<<< Agent#findResource(" + (classLoader == null ? "null" : classLoader.getClass().getName() + "@" + Integer.toString(System.identityHashCode(classLoader), 16)) + "," + arg + "): " + returned);
      }
      catch (final Throwable t) {
        AgentRule.logger.log(Level.SEVERE, "<><><><> AgentAgent.FindResource#exit", t);
      }
    }
  }

  public static class FindResources {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Argument(0) ClassLoader classLoader, final @Advice.Argument(1) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Enumeration<URL> returned) {
      try {
        String classpath = System.getProperty("java.class.path");
        final int index = classpath.indexOf("/opentracing-api-");
        final int start = classpath.lastIndexOf(File.pathSeparatorChar, index);
        final int end = classpath.indexOf(File.pathSeparatorChar, index);
        classpath = classpath.substring(start + 1, end != -1 ? end : classpath.length());
        if (!classpath.endsWith(".jar") && !classpath.endsWith("/"))
          classpath += "/";

        try (final RuleClassLoader ruleClassLoader = new RuleClassLoader(new URL[] {new URL("file", null, classpath)}, null)) {
          returned = ruleClassLoader.getResources(arg); // Why is findResources(arg) not returning expected results?
          returned.hasMoreElements(); // For some reason, if I don't call this, the returned value does not have any elements!!!!
        }

        if (AgentRule.logger.isLoggable(Level.FINEST))
          AgentRule.logger.finest("<<<<<<< Agent#findResources(" + (classLoader == null ? "null" : classLoader.getClass().getName() + "@" + Integer.toString(System.identityHashCode(classLoader), 16)) + "," + arg + "): " + returned);
      }
      catch (final Throwable t) {
        AgentRule.logger.log(Level.SEVERE, "<><><><> AgentAgent.FindResources#exit", t);
      }
    }
  }
}