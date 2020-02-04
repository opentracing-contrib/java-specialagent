package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;

import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class AbstractProcessorChainRule extends AgentRule {
    @Override
    public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
        return Collections.singletonList(
                builder
                        .type(hasSuperType(named("org.mule.runtime.core.privileged.processor.chain.AbstractMessageProcessorChain")))//.and(is(TypeDescription.Generic.Builder.))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                                    final TypeDescription typeDescription,
                                                                    final ClassLoader classLoader, final JavaModule module) {
                                return builder.visit(Advice.to(OnExit.class).on(named("resolveInterceptors")));
                            }
                        })
        );
    }

    public static class OnExit {
        @Advice.OnMethodExit
        public static void exit(final @Advice.Origin String origin,
                                @Advice.FieldValue(value = "muleContext", typing = Assigner.Typing.DYNAMIC) Object muleContext,
                                @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object interceptors) {
            if (isEnabled("AbstractProcessorChainRule", origin))
                interceptors = AbstractProcessorChainIntercept.exit(muleContext, interceptors);
        }
    }
}
