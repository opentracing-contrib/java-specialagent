package io.opentracing.contrib.specialagent.concurrent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import io.opentracing.Tracer;
import io.opentracing.contrib.concurrent.TracedRunnable;
import io.opentracing.contrib.specialagent.AgentPlugin;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class ExecutorAgentPlugin implements AgentPlugin {
  @Override
  public AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
//      .with(AgentBuilder.Listener.StreamWriting.toSystemError())
      .ignore(none())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(isSubTypeOf(Executor.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(ExecutorAgentPlugin.class).on(named("execute").and(takesArguments(Runnable.class))));
        }});
  }

  @Advice.OnMethodEnter
  public static void exit(@Advice.Origin Method method, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Runnable arg) throws Exception {
    System.out.println(">>>>>> " + method);
    final Tracer tracer = GlobalTracer.get();
    if (tracer.activeSpan() != null)
      arg = new TracedRunnable(arg, tracer);
  }
}