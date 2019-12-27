package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.mule.runtime.core.internal.event.DefaultEventContext;

import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class EventContextRule extends AgentRule {
    @Override
    public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
        return Collections.singletonList(
                builder
                        .type(named("org.mule.runtime.core.internal.event.DefaultEventContext"))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                                    final TypeDescription typeDescription,
                                                                    final ClassLoader classLoader, final JavaModule module) {
                                return builder.visit(Advice.to(EventContextIntercept.class).on(isConstructor()));
                            }
                        })
        );
    }

    public static class EventContextIntercept {
        @Advice.OnMethodExit
        public static void exit(final @Advice.Origin String origin, final @Advice.This Object thiz) {
            if (!isEnabled(origin))
                return;

            DefaultEventContext eventContext = (DefaultEventContext) thiz;

            Span span = GlobalTracer.get().activeSpan();
            String correlationId = eventContext.getCorrelationId();
            if (span == null || correlationId == null) {
                return;
            }

            SpanAssociations.get().associateSpan(correlationId, span);
        }
    }
}
