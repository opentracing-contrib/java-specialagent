package io.opentracing.contrib.specialagent.thrift;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import java.util.Arrays;
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

public class ThriftAsyncMethodCallbackAgentRule implements AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(String agentArgs) throws Exception {
    final Narrowable builder = new AgentBuilder.Default()
        .ignore(none())
        .with(RedefinitionStrategy.RETRANSFORMATION)
        .with(InitializationStrategy.NoOp.INSTANCE)
        .with(TypeStrategy.Default.REDEFINE)
        .type(hasSuperType(named("org.apache.thrift.async.AsyncMethodCallback")));

    return Arrays.asList(builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(OnComplete.class).on(named("onComplete")));
      }
    }), builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(OnError.class).on(named("onError")));
      }
    }));
  }

  public static class OnComplete {
    @Advice.OnMethodExit
    public static void exit() {
      if (AgentRuleUtil.isEnabled()) {
        ThriftAsyncMethodCallbackAgentIntercept.onComplete();
      }
    }
  }

  public static class OnError {
    @Advice.OnMethodExit
    public static void exit(@Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object exception) {
      if (AgentRuleUtil.isEnabled()) {
        ThriftAsyncMethodCallbackAgentIntercept.onError(exception);
      }
    }
  }
}
