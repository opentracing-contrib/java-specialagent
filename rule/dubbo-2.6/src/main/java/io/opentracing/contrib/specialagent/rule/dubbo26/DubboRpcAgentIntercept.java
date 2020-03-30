/* Copyright 2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.dubbo26;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.dubbo.rpc.Filter;

public class DubboRpcAgentIntercept {
  @SuppressWarnings("unchecked")
  public static Object exit(final Object returned) {
    if (!(returned instanceof List))
      return returned;

    final List<Filter> filters = (List<Filter>)returned;
    for (final Filter filter : filters)
      if (filter instanceof DubboRpcFilter)
        return filters;

    final ArrayList<Filter> newFilters = new ArrayList<>(filters);
    final DubboRpcFilter filter = new DubboRpcFilter();
    newFilters.add(filter);
    return newFilters;
  }
}