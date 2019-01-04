package io.opentracing.contrib.specialagent.concurrent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.opentracing.Tracer;
import io.opentracing.contrib.concurrent.TracedCallable;
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

public class ScheduledCallableAgentPlugin implements AgentPlugin {
  @Override
  public AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
//      .with(AgentBuilder.Listener.StreamWriting.toSystemError())
      .ignore(none())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(isSubTypeOf(ScheduledExecutorService.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(ScheduledCallableAgentPlugin.class).on(named("schedule").and(takesArguments(Callable.class, long.class, TimeUnit.class))));
        }});
  }

  @Advice.OnMethodEnter
  public static void exit(@Advice.Origin Method method, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Callable<?> arg) throws Exception {
    System.out.println(">>>>>> " + method);
    final Tracer tracer = GlobalTracer.get();
    if (tracer.activeSpan() != null)
      arg = new TracedCallable<>(arg, tracer);
  }
}