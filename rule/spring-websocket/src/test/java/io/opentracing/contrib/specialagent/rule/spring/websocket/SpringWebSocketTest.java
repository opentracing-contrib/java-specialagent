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

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.messaging.simp.stomp.DefaultStompSession;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.rule.spring.websocket.TracingChannelInterceptor;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class SpringWebSocketTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testInterceptors() {
    final DelegatingWebSocketMessageBrokerConfiguration configuration = new DelegatingWebSocketMessageBrokerConfiguration();
    final List<ChannelInterceptor> inboundInterceptors = configuration.clientInboundChannel().getInterceptors();

    final ChannelInterceptor inboundInterceptor = inboundInterceptors.get(inboundInterceptors.size() - 1);
    assertTrue(inboundInterceptor instanceof TracingChannelInterceptor);

    final List<ChannelInterceptor> outboundInterceptors = configuration.clientOutboundChannel().getInterceptors();
    final ChannelInterceptor outboundInterceptor = outboundInterceptors.get(inboundInterceptors.size() - 1);
    assertTrue(outboundInterceptor instanceof TracingChannelInterceptor);
  }

  @Test
  public void testSend(final MockTracer tracer) {
    final StompSession stompSession = new DefaultStompSession(new StompSessionHandlerAdapter() {}, new StompHeaders());

    try {
      stompSession.send("endpoint", "Hello");
    }
    catch (final Exception ignore) {
    }

    assertEquals(1, tracer.finishedSpans().size());
  }
}
