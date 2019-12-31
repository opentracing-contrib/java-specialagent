package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SpanManagerInterceptor implements ProcessorInterceptor {
    private Logger LOGGER = LoggerFactory.getLogger(SpanManagerInterceptor.class);

    @Override
    public CompletableFuture<InterceptionEvent> around(ComponentLocation location,
                                                       Map<String, ProcessorParameterValue> parameters,
                                                       InterceptionEvent event, InterceptionAction action) {
        String correlationId = event.getCorrelationId();
        if (correlationId == null)
            return action.proceed();

        Span span = SpanAssociations.get().retrieveSpan(correlationId);
        if (span == null)
            return action.proceed();

        Span processorSpan = GlobalTracer.get()
                .buildSpan(getStepName(parameters))
                .asChildOf(span)
                .start();

        Scope inScope = GlobalTracer.get().activateSpan(processorSpan);

        return action.proceed().exceptionally(ex -> {
//            processorSpan.setTag()
//            apmHandler.handleExceptionEvent(span, location, parameters, event, ex);
            throw new RuntimeException(ex);

        }).thenApply(finalEvent -> {
            inScope.close();
            processorSpan.finish();
            return finalEvent;
        });
    }

    public static String getStepName(Map<String, ProcessorParameterValue> parameters) {
        ProcessorParameterValue nameParam = parameters.get("doc:name");

        if (nameParam == null)
            return "unnamed";

        return nameParam.providedValue();
    }
}
