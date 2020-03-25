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

import org.mule.runtime.core.internal.event.DefaultEventContext;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class EventContextRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Collections.singletonList(builder
      .type(named("org.mule.runtime.core.internal.event.DefaultEventContext"))
      .transform(new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(EventContextRule.class).on(isConstructor()));
        }
      }));
  }

  @Advice.OnMethodExit
  public static void exit(final @Advice.Origin String origin, final @Advice.This Object thiz) {
    if (!isEnabled(EventContextRule.class.getName(), origin))
      return;

    final Span span = GlobalTracer.get().activeSpan();
    if (span == null)
      return;

    final String correlationId = ((DefaultEventContext)thiz).getCorrelationId();
    if (correlationId != null)
      SpanAssociations.get().associateSpan(correlationId, span);
  }
}