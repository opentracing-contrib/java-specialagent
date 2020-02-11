package io.opentracing.contrib.specialagent.rule.mule4;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.internal.processor.interceptor.ReactiveAroundInterceptorAdapter;

import java.util.List;

public class AbstractProcessorChainIntercept {

    @SuppressWarnings("unchecked")
    public static Object exit(Object muleContext, Object interceptors) {
        ReactiveAroundInterceptorAdapter adapter = new ReactiveAroundInterceptorAdapter(SpanManagerInterceptor::new);
        try {
            ((MuleContext) muleContext).getInjector().inject(adapter);
        } catch (MuleException e) {
            throw new MuleRuntimeException(e);
        }
        ((List) interceptors).add(0, adapter);

        return interceptors;
    }
}
