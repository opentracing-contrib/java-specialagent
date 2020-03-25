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

package io.opentracing.contrib.specialagent.rule.cxf.interceptor;

import java.util.HashSet;
import java.util.Set;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.tracing.opentracing.OpenTracingClientStopInterceptor;

public class ClientSpanTagInterceptor extends AbstractSpanTagInterceptor {
  private static final String TRACE_SPAN = "org.apache.cxf.tracing.client.opentracing.span";

  public ClientSpanTagInterceptor() {
    super(Phase.RECEIVE);
  }

  @Override
  public Set<String> getBefore() {
    final Set<String> before = new HashSet<>();
    before.add(OpenTracingClientStopInterceptor.class.getName());
    return before;
  }

  @Override
  protected String getTraceSpanKey() {
    return TRACE_SPAN;
  }
}