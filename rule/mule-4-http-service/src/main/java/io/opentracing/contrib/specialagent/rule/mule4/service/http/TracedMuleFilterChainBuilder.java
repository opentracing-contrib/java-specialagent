/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.mule4.service.http;

import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyRequestDispatcherFilter;

import io.opentracing.Tracer;
import io.opentracing.contrib.grizzly.http.server.TracedFilterChainBuilder;

public class TracedMuleFilterChainBuilder extends TracedFilterChainBuilder {
  public TracedMuleFilterChainBuilder(final FilterChainBuilder builder, final Tracer tracer) {
    super(builder, tracer);
  }

  @Override
  public FilterChain build() {
    final int dispatcherFilter = this.indexOfType(GrizzlyRequestDispatcherFilter.class);
    if (dispatcherFilter != -1) {
      // If contains an GrizzlyRequestDispatcherFilter
      // @see https://github.com/mulesoft/mule-http-service/blob/1.4.7/src/main/java/org/mule/service/http/impl/service/server/grizzly/GrizzlyServerManager.java#L111
      addTracingFiltersAt(4);
    }

    return super.build();
  }
}