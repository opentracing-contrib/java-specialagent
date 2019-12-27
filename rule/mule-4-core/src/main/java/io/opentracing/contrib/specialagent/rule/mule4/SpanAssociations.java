package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Span;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class SpanAssociations {

    private static final SpanAssociations INSTANCE = new SpanAssociations();
    private static final Map<Object, Span> spanAssociations = Collections.synchronizedMap(new WeakHashMap<Object, Span>());

    private SpanAssociations() {
    }

    public static SpanAssociations get() {
        return INSTANCE;
    }

    /**
     * This method establishes an association between an application object
     * (i.e. the subject of the instrumentation) and a span. Once the
     * application object is no longer being used, the association with the
     * span will automatically be discarded.
     *
     * @param obj The application object to be associated with the span
     * @param span The span
     */
    public void associateSpan(Object obj, Span span) {
        spanAssociations.putIfAbsent(obj, span);
    }

    /**
     * This method retrieves the span associated with the supplied application
     * object.
     *
     * @param obj The application object
     * @return The span, or null if no associated span exists
     */
    public Span retrieveSpan(Object obj) {
        return spanAssociations.get(obj);
    }

}
