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

package io.opentracing.contrib.specialagent.rule.spring.messaging;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;

import java.util.List;

import java.util.concurrent.Callable;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class SpringKafkaMessagingTest {
  @ClassRule
  public static final EmbeddedKafkaRule embeddedKafkaRule = new EmbeddedKafkaRule(1);

  @BeforeClass
  public static void setup() {
    System.setProperty("spring.kafka.bootstrap-servers", embeddedKafkaRule.getEmbeddedKafka().getBrokersAsString());
    System.setProperty("spring.cloud.stream.kafka.binder.zkNodes", embeddedKafkaRule.getEmbeddedKafka().getZookeeperConnectionString());
    System.setProperty("spring.kafka.consumer.group-id", "testGroup");
    System.setProperty("spring.cloud.stream.bindings.input.destination", "testDestination");
    System.setProperty("spring.cloud.stream.bindings.input.group", "testGroup");
    System.setProperty("spring.cloud.stream.bindings.output.destination", "testDestination");
    System.setProperty("spring.kafka.producer.value-serializer", "org.springframework.kafka.support.serializer.JsonSerializer");
  }

  @Test
  public void test(final MockTracer tracer) {
    try (final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(KafkaConfiguration.class)) {
      final Sender sender = context.getBean(Sender.class);
      final Receiver receiver = context.getBean(Receiver.class);

      sender.send("Ping");

      await().atMost(5, SECONDS).until(new Callable<List<String>>() {
        @Override
        public List<String> call() throws Exception {
          return receiver.getReceivedMessages();
        }
      }, hasSize(1));
    }

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertThat(finishedSpans).hasSize(2);

    final MockSpan outputSpan = getSpanByOperation("send:output", tracer);
    assertThat(outputSpan.parentId()).isEqualTo(0);
    assertThat(outputSpan.tags()).hasSize(3);
    assertThat(outputSpan.tags()).containsEntry(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER);
    assertThat(outputSpan.tags()).containsEntry(Tags.COMPONENT.getKey(), "spring-messaging");
    assertThat(outputSpan.tags()).containsEntry(Tags.MESSAGE_BUS_DESTINATION.getKey(), "output");

    final MockSpan inputSpan = getSpanByOperation("receive:input", tracer);
    assertThat(inputSpan.parentId()).isEqualTo(outputSpan.context().spanId());
    assertThat(inputSpan.tags()).hasSize(3);
    assertThat(inputSpan.tags()).containsEntry(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER);
    assertThat(inputSpan.tags()).containsEntry(Tags.COMPONENT.getKey(), "spring-messaging");
    assertThat(inputSpan.tags()).containsEntry(Tags.MESSAGE_BUS_DESTINATION.getKey(), "input");

    assertThat(outputSpan.startMicros()).isLessThanOrEqualTo(inputSpan.startMicros());
  }

  private static MockSpan getSpanByOperation(final String operationName, final MockTracer tracer) {
    for (final MockSpan span : tracer.finishedSpans())
      if (operationName.equals(span.operationName()))
        return span;

    throw new RuntimeException(String.format("Span for operation '%s' doesn't exist", operationName));
  }
}