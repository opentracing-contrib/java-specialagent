package io.opentracing.contrib.specialagent.rule.mule4.service.http;

import io.opentracing.Tracer;
import io.opentracing.contrib.grizzly.http.server.TracedFilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyRequestDispatcherFilter;

public class TracedMuleFilterChainBuilder extends TracedFilterChainBuilder {


    public TracedMuleFilterChainBuilder(FilterChainBuilder builder, Tracer tracer) {
        super(builder, tracer);
    }

    @Override
    public FilterChain build() {
        final int dispatcherFilter = this.indexOfType(GrizzlyRequestDispatcherFilter.class);
        if (dispatcherFilter != -1) {
            // If contains an GrizzlyRequestDispatcherFilter
            // See: https://github.com/mulesoft/mule-http-service/blob/1.4.7/src/main/java/org/mule/service/http/impl/service/server/grizzly/GrizzlyServerManager.java#L111
            addTracingFiltersAt(4);
        }

        return super.build();
    }
}
