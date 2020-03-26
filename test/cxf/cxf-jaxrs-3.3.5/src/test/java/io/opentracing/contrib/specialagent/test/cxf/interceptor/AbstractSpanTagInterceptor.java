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

package io.opentracing.contrib.specialagent.test.cxf.interceptor;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.tracing.AbstractTracingProvider;
import org.apache.cxf.tracing.opentracing.TraceScope;

import io.opentracing.Span;

public abstract class AbstractSpanTagInterceptor extends AbstractTracingProvider implements PhaseInterceptor<Message> {
  public static final String SPAN_TAG_KEY = "customized_tag";
  public static final String SPAN_TAG_VALUE = "we tag what we want";

  private String phase;

  public AbstractSpanTagInterceptor(final String phase) {
    this.phase = phase;
  }

  @Override
  public void handleMessage(final Message message) throws Fault {
    @SuppressWarnings("unchecked")
    final TraceScopeHolder<TraceScope> holder = (TraceScopeHolder<TraceScope>)message.getExchange().get(getTraceSpanKey());
    if (holder != null && holder.getScope() != null) {
      final TraceScope traceScope = holder.getScope();
      final Span span = traceScope.getSpan();
      span.setTag(SPAN_TAG_KEY, SPAN_TAG_VALUE);
    }
  }

  protected abstract String getTraceSpanKey();

  @Override
  public void handleFault(final Message message) {
  }

  @Override
  public Set<String> getAfter() {
    return Collections.emptySet();
  }

  @Override
  public String getId() {
    return getClass().getName();
  }

  @Override
  public String getPhase() {
    return phase;
  }

  @Override
  public Collection<PhaseInterceptor<? extends Message>> getAdditionalInterceptors() {
    return null;
  }
}