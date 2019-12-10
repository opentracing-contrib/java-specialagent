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

package io.opentracing.contrib.specialagent.test.spring.websocket;

import io.opentracing.contrib.specialagent.TestUtil;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootApplication
public class SpringWebSocketITest {
  private static final String SEND_HELLO_MESSAGE_ENDPOINT = "/app/hello";
  private static final String SUBSCRIBE_GREETINGS_ENDPOINT = "/topic/greetings";

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
    return new CommandLineRunner() {
      @Override
      public void run(String... args) throws Exception {
        String url = "ws://localhost:8080/test-websocket";

        WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession stompSession = stompClient.connect(url, new StompSessionHandlerAdapter() {
        }).get(10, TimeUnit.SECONDS);

        stompSession.subscribe(SUBSCRIBE_GREETINGS_ENDPOINT, new GreetingStompFrameHandler());
        stompSession.send(SEND_HELLO_MESSAGE_ENDPOINT, "Hello");
      }
    };
  }

  private static List<Transport> createTransportClient() {
    List<Transport> transports = new ArrayList<>();
    transports.add(new WebSocketTransport(new StandardWebSocketClient()));
    return transports;
  }

  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();

    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(6);

    try(ConfigurableApplicationContext context = SpringApplication.run(SpringWebSocketITest.class, args)) {
      TestUtil.checkSpan("websocket", 6, latch);
    }

  }

  private static class GreetingStompFrameHandler implements StompFrameHandler {

    @Override
    public Type getPayloadType(StompHeaders stompHeaders) {
      return String.class;
    }

    @Override
    public void handleFrame(StompHeaders stompHeaders, Object o) {
    }
  }
}