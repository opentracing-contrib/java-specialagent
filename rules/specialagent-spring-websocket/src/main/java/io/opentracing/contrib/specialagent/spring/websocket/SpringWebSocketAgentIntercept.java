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

package io.opentracing.contrib.specialagent.spring.websocket;

import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;

public class SpringWebSocketAgentIntercept {
  private static final ThreadLocal<Span> spanHolder = new ThreadLocal<>();

  public static void clientInboundChannel(Object returned) {
    AbstractSubscribableChannel channel = (AbstractSubscribableChannel) returned;
    List<ChannelInterceptor> interceptors = new ArrayList<>(channel.getInterceptors());
    interceptors.add(new TracingChannelInterceptor(GlobalTracer.get(),
        Tags.SPAN_KIND_SERVER));
    channel.setInterceptors(interceptors);
  }

  public static void clientOutboundChannel(Object returned) {
    AbstractSubscribableChannel channel = (AbstractSubscribableChannel) returned;
    List<ChannelInterceptor> interceptors = new ArrayList<>(channel.getInterceptors());
    interceptors.add(new TracingChannelInterceptor(GlobalTracer.get(),
        Tags.SPAN_KIND_CLIENT));
    channel.setInterceptors(interceptors);
  }

  public static void sendEnter(Object arg) {
    StompHeaders headers = (StompHeaders) arg;
    final Span span = GlobalTracer.get().buildSpan(headers.getDestination())
        .withTag(Tags.COMPONENT, "stomp-session")
        .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        .start();
    GlobalTracer.get()
        .inject(span.context(), Format.Builtin.TEXT_MAP, new StompHeadersInjectAdapter(headers));
    spanHolder.set(span);
  }

  public static void sendExit(Throwable thrown) {
    final Span span = spanHolder.get();
    if (span == null) {
      return;
    }
    if (thrown != null) {
      Tags.ERROR.set(span, Boolean.TRUE);
      final HashMap<String, Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", Tags.ERROR.getKey());
      errorLogs.put("error.object", thrown);
      span.log(errorLogs);
    }
    span.finish();
    spanHolder.remove();
  }
}