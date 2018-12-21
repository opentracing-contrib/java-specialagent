package io.opentracing.contrib.specialagent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import net.bytebuddy.agent.builder.AgentBuilder;
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

public class ClassLoaderAgent {
  private static final Method defineClass;

  static {
    try {
      defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
    }
    catch (final NoSuchMethodException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    buildAgent(agentArgs)
//      .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
      .installOn(inst);
  }

  public static AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
      .ignore(none())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(isSubTypeOf(ClassLoader.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
//          return builder.method(named("findClass").and(ElementMatchers.returns(Class.class).and(ElementMatchers.takesArguments(String.class)))).intercept(MethodDelegation.to(ClassLoaderAgent.class).andThen(SuperMethodCall.INSTANCE));
          return builder.visit(Advice.to(ClassLoaderAgent.class).on(not(isAbstract()).and(named("findClass").and(ElementMatchers.returns(Class.class).and(ElementMatchers.takesArguments(String.class))))));
        }});
  }

  @Advice.OnMethodExit(onThrowable=ClassNotFoundException.class)
  public static void exit(@Advice.Origin Method method, @Advice.This ClassLoader thiz, @Advice.Argument(0) String arg, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Class<?> returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) ClassNotFoundException e) throws IllegalAccessException, InvocationTargetException {
    System.err.println(">>>>>>>> findClass(" + arg + ")");
    if (e != null) {
      System.err.println(">>>>>>>> Agent.findClass(" + arg + ")");
      final byte[] bytecode = Agent.findClass(thiz, arg);
      if (bytecode == null)
        return;

      e = null;
      System.err.println(">>>>>>>> defineClass(" + arg + ")");
      returned = (Class<?>)defineClass.invoke(thiz, arg, bytecode, 0, bytecode.length, null);
    }
  }
}