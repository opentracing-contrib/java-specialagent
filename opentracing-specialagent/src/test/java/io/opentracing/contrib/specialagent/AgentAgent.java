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
import java.io.InputStream;
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
 * {@link Agent#findClass(ClassLoader,String)} method to override its returned
 * value.
 * <p>
 * This class is used for testing of {@link ClassLoaderAgent}.
 *
 * @author Seva Safris
 */
public class AgentAgent {
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    System.setProperty("AgentAgent", AgentAgent.class.getProtectionDomain().getCodeSource().getLocation().toString());
    final Narrowable builder = new AgentBuilder.Default()
      .ignore(none())
      .disableClassFormatChanges()
      .with(new DebugListener())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(is(Agent.class));

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(FindClass.class).on(isStatic().and(named("findClass").and(returns(byte[].class).and(takesArguments(ClassLoader.class, String.class))))));
        }})
      .installOn(inst);

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(FindResource.class).on(isStatic().and(named("findResource").and(returns(URL.class).and(takesArguments(ClassLoader.class, String.class))))));
        }})
      .installOn(inst);

    builder
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(FindResources.class).on(isStatic().and(named("findResources").and(returns(Enumeration.class).and(takesArguments(ClassLoader.class, String.class))))));
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

        try (final PluginClassLoader pluginClassLoader = new PluginClassLoader(new URL[] {new URL("file", null, classpath)}, null)) {
          final URL resource = pluginClassLoader.findResource(arg.replace('.', '/').concat(".class"));
          if (resource != null) {
            try (final InputStream in = resource.openStream()) {
              returned = Util.readBytes(in);
            }
          }
        }
        System.err.println("<<<<<<< Agent#findClass(" + (classLoader == null ? "null" : classLoader.getClass().getName() + "@" + Integer.toString(System.identityHashCode(classLoader), 16)) + "," + arg + "): " + returned);
      }
      catch (final Throwable t) {
        System.err.println("AgentAgent.FindClass#exit: " + t);
        t.printStackTrace();
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

        try (final PluginClassLoader pluginClassLoader = new PluginClassLoader(new URL[] {new URL("file", null, classpath)}, null)) {
          returned = pluginClassLoader.findResource(arg);
        }

        System.err.println("<<<<<<< Agent#findResource(" + (classLoader == null ? "null" : classLoader.getClass().getName() + "@" + Integer.toString(System.identityHashCode(classLoader), 16)) + "," + arg + "): " + returned);
      }
      catch (final Throwable t) {
        System.err.println("AgentAgent.FindResource#exit: " + t);
        t.printStackTrace();
      }
    }
  }

  public static class FindResources {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Argument(0) ClassLoader classLoader, final @Advice.Argument(1) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Enumeration<URL> returned) {
      System.err.println(returned);
      try {
        String classpath = System.getProperty("java.class.path");
        final int index = classpath.indexOf("/opentracing-api-");
        final int start = classpath.lastIndexOf(File.pathSeparatorChar, index);
        final int end = classpath.indexOf(File.pathSeparatorChar, index);
        classpath = classpath.substring(start + 1, end != -1 ? end : classpath.length());
        if (!classpath.endsWith(".jar") && !classpath.endsWith("/"))
          classpath += "/";

        try (final PluginClassLoader pluginClassLoader = new PluginClassLoader(new URL[] {new URL("file", null, classpath)}, null)) {
          returned = pluginClassLoader.getResources(arg); // Why is findResources(arg) not returning expected results?
        }

        System.err.println("<<<<<<< Agent#findResources(" + (classLoader == null ? "null" : classLoader.getClass().getName() + "@" + Integer.toString(System.identityHashCode(classLoader), 16)) + "," + arg + "): " + returned);
      }
      catch (final Throwable t) {
        System.err.println("AgentAgent.FindResources#exit: " + t);
        t.printStackTrace();
      }
    }
  }
}