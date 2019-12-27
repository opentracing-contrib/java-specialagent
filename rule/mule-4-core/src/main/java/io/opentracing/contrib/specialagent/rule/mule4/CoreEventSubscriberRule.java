package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;
import org.mule.runtime.api.event.Event;

import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class CoreEventSubscriberRule extends AgentRule {
    @Override
    public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
        return Collections.singletonList(
                builder
                        .type(hasSuperType(named("org.reactivestreams.Subscriber")))//.and(is(TypeDescription.Generic.Builder.))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                                    final TypeDescription typeDescription,
                                                                    final ClassLoader classLoader, final JavaModule module) {
                                return builder.visit(Advice.to(SubscriberIntercept.class).on(named("onNext")));
                            }
                        })
        );
    }


    public static class SubscriberIntercept {
        public static final ThreadLocal<Scope> scopeHolder = new ThreadLocal<>();

        @Advice.OnMethodEnter
        public static void enter(final @Advice.Origin String origin,
                                 final @Advice.This Object thiz,
                                 final @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) Object event) {
            if (!isEnabled(origin))
                return;

            if (!(event instanceof Event))
                return;

            String correlationId = ((Event) event).getCorrelationId();
            if (correlationId == null)
                return;

            Span span = SpanAssociations.get().retrieveSpan(correlationId);
            if (span == null)
                return;

            scopeHolder.set(GlobalTracer.get().scopeManager().activate(span));
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(final @Advice.Origin String origin,
                                final @Advice.Thrown(typing = Assigner.Typing.DYNAMIC) Throwable thrown) {
            if (!isEnabled(origin))
                return;

            Scope scope = scopeHolder.get();
            if (scope == null)
                return;

            scope.close();
            scopeHolder.remove();
        }
    }
}
