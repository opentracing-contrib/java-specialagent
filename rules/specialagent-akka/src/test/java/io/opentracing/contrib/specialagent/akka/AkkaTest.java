/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.akka;

import static akka.pattern.Patterns.ask;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;


@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class AkkaTest {
  private static ActorSystem system;

  @BeforeClass
  public static void beforeClass() {
    system = ActorSystem.create("testSystem");
  }

  @AfterClass
  public static void afterClass() throws Exception {
    if (system != null) {
      Await.result(system.terminate(), getDefaultDuration());
    }
  }

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  //@Test
  public void testTell(final MockTracer tracer) {
    ActorRef actorRef = system.actorOf(TestActor.props(tracer), "tell");

    actorRef.tell("tell", ActorRef.noSender());

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    for (MockSpan span : spans) {
      assertEquals(AkkaAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
    }
  }

  @Test
  public void testAsk(final MockTracer tracer) throws Exception {
    ActorRef actorRef = system.actorOf(TestActor.props(tracer), "ask");
    Timeout timeout = new Timeout(getDefaultDuration());

    Future<Object> future = ask(actorRef, "ask", timeout);
    Boolean isSpanNull = (Boolean) Await.result(future, getDefaultDuration());
    assertFalse(isSpanNull);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    for (MockSpan span : spans) {
      assertEquals(AkkaAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
    }
  }

  private static FiniteDuration getDefaultDuration() {
    return Duration.create(15, "seconds");
  }

  static class TestActor extends AbstractActor {
    private final MockTracer tracer;

    TestActor(MockTracer tracer) {
      this.tracer = tracer;
    }

    static Props props(MockTracer tracer) {
      return Props.create(TestActor.class, () -> new TestActor(tracer));
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchAny(x -> {
            final Span span = tracer.activeSpan();
            getSender().tell(span == null, getSelf());
          })
          .build();
    }
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }

}