package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;
import org.mule.runtime.api.event.Event;
import org.slf4j.MDC;

import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class PrivilegedEventRule extends AgentRule {
    @Override
    public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
        return Collections.singletonList(
                builder
                        .type(named("org.mule.runtime.core.privileged.event.PrivilegedEvent"))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                                    final TypeDescription typeDescription,
                                                                    final ClassLoader classLoader, final JavaModule module) {
                                return builder.visit(Advice.to(OnExit.class).on(named("setCurrentEvent")));
                            }
                        })
        );
    }


    public static class OnExit {
        public static final String TRACE_ID_MDC_KEY = "traceId";
        public static final String SPAN_ID_MDC_KEY = "spanId";

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(final @Advice.Origin String origin,
                                final @Advice.Thrown(typing = Assigner.Typing.DYNAMIC) Throwable thrown,
                                final @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) Object event) {
            if (!isEnabled("PrivilegedEventRule", origin))
                return;

            if (event == null) {
                MDC.remove(TRACE_ID_MDC_KEY);
                MDC.remove(SPAN_ID_MDC_KEY);

                return;
            }

            String correlationId = ((Event) event).getCorrelationId();
            if (correlationId == null)
                return;

            Span span = SpanAssociations.get().retrieveSpan(correlationId);
            if (span == null)
                return;

            MDC.put(TRACE_ID_MDC_KEY, span.context().toTraceId());
            MDC.put(SPAN_ID_MDC_KEY, span.context().toSpanId());
        }
    }
}
