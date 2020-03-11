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

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.DefaultStompSession;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class SpringWebSocketTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testInterceptors(final MockTracer tracer) {
    final DelegatingWebSocketMessageBrokerConfiguration configuration = new DelegatingWebSocketMessageBrokerConfiguration();

    final AbstractSubscribableChannel inboundChannel = configuration.clientInboundChannel();
    inboundChannel.setBeanName("clientInboundChannel");

    final AbstractSubscribableChannel outboundChannel = configuration.clientOutboundChannel();
    outboundChannel.setBeanName("clientOutboundChannel");

    outboundChannel.subscribe(new SubProtocolWebSocketHandler(inboundChannel, outboundChannel));
    inboundChannel.subscribe(new SubProtocolWebSocketHandler(inboundChannel, outboundChannel));

    configuration.clientInboundChannelExecutor().initialize();
    configuration.clientOutboundChannelExecutor().initialize();

    final Map<String,Object> headers = Collections.<String,Object>singletonMap("simpMessageType", SimpMessageType.MESSAGE);
    final GenericMessage<String> message = new GenericMessage<>("test", headers);
    outboundChannel.send(message);
    inboundChannel.send(message);

    await().atMost(15, TimeUnit.SECONDS).until(TestUtil.reportedSpansSize(tracer), equalTo(2));
    assertEquals(2, tracer.finishedSpans().size());
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