package io.opentracing.contrib.specialagent.rule.dubbo26;

import com.alibaba.dubbo.rpc.Filter;

import java.util.ArrayList;
import java.util.List;

public class DubboAgentIntercept {
    public static Object  exit(final Object returned) {

        if (returned instanceof List) {
            List<Filter> filters = (List<Filter>) returned;
            for (final Filter filter : filters) {
                if (filter instanceof DubboRpcFilter) {
                    return filters;
                }
            }

            final ArrayList<Filter> newFilters = new ArrayList<Filter>(filters);
            final DubboRpcFilter filter = new DubboRpcFilter();
            newFilters.add(filter);
            return newFilters;
        } else {
            return returned;
        }
    }
}
