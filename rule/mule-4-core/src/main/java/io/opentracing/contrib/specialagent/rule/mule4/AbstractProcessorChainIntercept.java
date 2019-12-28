package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Operators.lift;

public class AbstractProcessorChainIntercept {

    @SuppressWarnings("unchecked")
    public static Object exit(Object interceptors) {
        ((List<BiFunction<Processor, ReactiveProcessor, ReactiveProcessor>>) interceptors).add(0,
                (processor, next) -> stream -> from(stream)
                        .transform(operator().andThen(next))
        );

        return interceptors;
    }

    public static Function<? super Publisher<CoreEvent>, ? extends Publisher<CoreEvent>> operator() {
        return lift((scannable, subscriber) -> new CoreSubscriber<CoreEvent>() {

            @Override
            public void onNext(CoreEvent event) {
                String correlationId = event.getCorrelationId();
                if (correlationId == null) {
                    subscriber.onNext(event);
                    return;
                }

                Span span = SpanAssociations.get().retrieveSpan(correlationId);
                if (span == null) {
                    subscriber.onNext(event);
                    return;
                }

                try (Scope inScope = GlobalTracer.get().scopeManager().activate(span)) {
                    subscriber.onNext(event);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }

            @Override
            public Context currentContext() {
                return subscriber.currentContext();
            }

            @Override
            public void onSubscribe(Subscription s) {
                subscriber.onSubscribe(s);
            }
        });
    }
}
