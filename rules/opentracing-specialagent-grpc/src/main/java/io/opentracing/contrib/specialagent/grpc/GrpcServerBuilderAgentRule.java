package io.opentracing.contrib.specialagent.grpc;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class GrpcServerBuilderAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(hasSuperType(named("io.grpc.ServerBuilder")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(GrpcServerBuilderAgentRule.class).on(named("addService").and(takesArguments(1))));
        }}));
  }

  @Advice.OnMethodEnter
  public static void enter(final @Advice.Origin String origin, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object service) {
    if (isEnabled(origin))
      service = GrpcServerAgentIntercept.addService(service);
  }
}