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

package io.opentracing.contrib.specialagent.rule.mule4;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Collections;

import org.mule.runtime.api.event.Event;
import org.slf4j.MDC;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class PrivilegedEventRule extends AgentRule {
  public static final String TRACE_ID_MDC_KEY = "traceId";
  public static final String SPAN_ID_MDC_KEY = "spanId";

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Collections.singletonList(builder
      .type(named("org.mule.runtime.core.privileged.event.PrivilegedEvent"))
      .transform(new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(PrivilegedEventRule.class).on(named("setCurrentEvent")));
        }
      }));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object event) {
    if (!isEnabled("PrivilegedEventRule", origin))
      return;

    if (event == null) {
      MDC.remove(TRACE_ID_MDC_KEY);
      MDC.remove(SPAN_ID_MDC_KEY);
      return;
    }

    final String correlationId = ((Event)event).getCorrelationId();
    if (correlationId == null)
      return;

    final Span span = SpanAssociations.get().retrieveSpan(correlationId);
    if (span == null)
      return;

    MDC.put(TRACE_ID_MDC_KEY, span.context().toTraceId());
    MDC.put(SPAN_ID_MDC_KEY, span.context().toSpanId());
  }
}