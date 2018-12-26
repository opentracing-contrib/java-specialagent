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
import java.security.ProtectionDomain;

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
import net.bytebuddy.matcher.ElementMatchers;
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
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    final Narrowable builder = new AgentBuilder.Default()
      .ignore(none())
      .disableClassFormatChanges()
//    .with(new DebugListener())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(isSubTypeOf(ClassLoader.class));

    builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(OnEnter.class).on(not(isAbstract()).and(named("findClass").and(ElementMatchers.returns(Class.class).and(ElementMatchers.takesArguments(String.class))))));
      }}).installOn(inst);

    builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(OnExit.class).on(not(isAbstract()).and(named("findClass").and(ElementMatchers.returns(Class.class).and(ElementMatchers.takesArguments(String.class))))));
      }}).installOn(inst);
  }

  public static class OnEnter {
    @Advice.OnMethodEnter
    public static void enter(@Advice.This ClassLoader thiz, @Advice.Argument(0) String arg) {
      try {
        System.err.println(">>>>>>>> " + (thiz == null ? "null" : thiz.getClass().getName() + "@" + Integer.toString(System.identityHashCode(thiz), 16)) + "#findClass(" + arg + ") ");
        final byte[] bytecode = Agent.findClass(thiz, arg);
        if (bytecode != null)
          throw new EarlyReturnException(bytecode);
      }
      catch (final NoClassDefFoundError e) {
        System.err.println("OnEnter: " + e);
      }
    }
  }

  public static class OnExit {
    @Advice.OnMethodExit(onThrowable = EarlyReturnException.class)
    public static void exit(@Advice.This ClassLoader thiz, @Advice.Argument(0) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Class<?> returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) EarlyReturnException thrown) throws ReflectiveOperationException {
      if (thrown == null)
        return;

      System.err.println("<<<<<<<< defineClass(" + arg + ")");
      final byte[] bytecode = (byte[])thrown.getReturnValue();
      final Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
      returned = (Class<?>)defineClass.invoke(thiz, arg, bytecode, 0, bytecode.length, null);
      thrown = null;
    }
  }
}