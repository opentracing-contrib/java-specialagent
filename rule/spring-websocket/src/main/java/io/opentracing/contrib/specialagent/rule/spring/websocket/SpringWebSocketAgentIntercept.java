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

package io.opentracing.contrib.specialagent.rule.spring.websocket;


import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringWebSocketAgentIntercept {
  private static final ThreadLocal<Span> spanHolder = new ThreadLocal<>();

  public static void messageChannelSend(final Object thiz) {
    final AbstractMessageChannel channel = (AbstractMessageChannel)thiz;
    for (ChannelInterceptor interceptor : channel.getInterceptors()) {
      if(interceptor instanceof TracingChannelInterceptor)
        return;
    }
    TracingChannelInterceptor tracingChannelInterceptor = null;
    if(channel.getBeanName().equals("clientOutboundChannel"))
      tracingChannelInterceptor = new TracingChannelInterceptor(GlobalTracer.get(), Tags.SPAN_KIND_CLIENT);
    else if(channel.getBeanName().equals("clientInboundChannel"))
      tracingChannelInterceptor = new TracingChannelInterceptor(GlobalTracer.get(), Tags.SPAN_KIND_SERVER);

    if(tracingChannelInterceptor != null)
      channel.addInterceptor(tracingChannelInterceptor);
  }

  public static void sendEnter(final Object arg) {
    final Tracer tracer = GlobalTracer.get();
    final StompHeaders headers = (StompHeaders)arg;
    final Span span = tracer.buildSpan(headers.getDestination())
      .withTag(Tags.COMPONENT, "stomp-session")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
      .start();

    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new StompHeadersInjectAdapter(headers));
    spanHolder.set(span);
  }

  public static void sendExit(final Throwable thrown) {
    final Span span = spanHolder.get();
    if (span == null)
      return;

    if (thrown != null)
      AgentRuleUtil.setErrorTag(span, thrown);

    span.finish();
    spanHolder.remove();
  }
}