package io.opentracing.contrib.specialagent.rule.dubbo27;

import org.apache.dubbo.rpc.Filter;

import java.util.ArrayList;
import java.util.List;

public class DubboAgentIntercept {
    @SuppressWarnings("unchecked")
    public static Object exit(final Object returned) {
        if (returned instanceof List) {
            List<Filter> filters = (List<Filter>) returned;
            for (final Filter filter : filters) {
                if (filter instanceof DubboFilter) {
                    return filters;
                }
            }
            final ArrayList<Filter> newFilters = new ArrayList<Filter>(filters);
            final DubboFilter filter = new DubboFilter();
            newFilters.add(filter);
            return newFilters;
        } else {
            return returned;
        }
    }
}
