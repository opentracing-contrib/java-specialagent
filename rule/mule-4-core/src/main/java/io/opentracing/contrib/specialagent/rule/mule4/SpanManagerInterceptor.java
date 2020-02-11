package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SpanManagerInterceptor implements ProcessorInterceptor {
    private static final String DOC_NAME = "doc:name";
    private static final String UNNAMED = "unnamed";
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
                .buildSpan(getDocName(parameters))
                .asChildOf(span)
                .start();
        Tags.COMPONENT.set(processorSpan, getComponentName(location));

        Scope inScope = GlobalTracer.get().activateSpan(processorSpan);

        return action.proceed().exceptionally(exception -> {
            processorSpan.setTag(Tags.ERROR, true);
            if (exception != null)
                span.log(errorLogs(exception));

            throw new RuntimeException(exception);
        }).thenApply(finalEvent -> {
            inScope.close();
            processorSpan.finish();
            return finalEvent;
        });
    }

    public static String getComponentName(ComponentLocation location) {
        return location.getComponentIdentifier().getIdentifier().getNamespace() + ":" + location.getComponentIdentifier().getIdentifier().getName();
    }

    public static String getDocName(Map<String, ProcessorParameterValue> parameters) {
        ProcessorParameterValue nameParam = parameters.get(DOC_NAME);

        if (nameParam == null)
            return UNNAMED;

        return nameParam.providedValue();
    }

    private static Map<String, Object> errorLogs(final Throwable throwable) {
        final Map<String, Object> errorLogs = new HashMap<>(2);
        errorLogs.put("event", Tags.ERROR.getKey());
        errorLogs.put("error.object", throwable);
        return errorLogs;
    }
}
