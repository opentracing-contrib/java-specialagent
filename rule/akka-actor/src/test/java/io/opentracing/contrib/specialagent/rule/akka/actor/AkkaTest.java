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

package io.opentracing.contrib.specialagent.rule.akka.actor;

import static akka.pattern.Patterns.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
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
    if (system != null)
      Await.result(system.terminate(), getDefaultDuration());
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testTell(final MockTracer tracer) {
    final ActorRef actorRef = system.actorOf(TestActor.props(tracer, false), "tell");

    actorRef.tell("tell", ActorRef.noSender());

    final ActorSelection actorSelection = system.actorSelection(actorRef.path());
    actorSelection.tell("tell-selection", ActorRef.noSender());

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(4));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(4, spans.size());
    for (final MockSpan span : spans)
      assertEquals(AkkaAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
  }

  @Test
  public void testAsk(final MockTracer tracer) throws Exception {
    final ActorRef actorRef = system.actorOf(TestActor.props(tracer, false), "ask");
    final Timeout timeout = new Timeout(getDefaultDuration());

    final Future<Object> future = ask(actorRef, "ask", timeout);
    final Boolean isSpanNull = (Boolean)Await.result(future, getDefaultDuration());
    assertFalse(isSpanNull);

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(2));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    for (final MockSpan span : spans)
      assertEquals(AkkaAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
  }

  @Test
  public void testForward(final MockTracer tracer) throws Exception {
    final ActorRef actorRef = system.actorOf(TestActor.props(tracer, true), "forward");
    final Timeout timeout = new Timeout(getDefaultDuration());

    final Future<Object> future = ask(actorRef, "forward", timeout);
    final Boolean isSpanNull = (Boolean)Await.result(future, getDefaultDuration());
    assertFalse(isSpanNull);

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(2));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    for (final MockSpan span : spans)
      assertEquals(AkkaAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
  }

  private static FiniteDuration getDefaultDuration() {
    return Duration.create(15, "seconds");
  }

  static class TestActor extends AbstractActor {
    private final MockTracer tracer;
    private final boolean forward;

    TestActor(final MockTracer tracer, final boolean forward) {
      this.tracer = tracer;
      this.forward = forward;
    }

    static Props props(final MockTracer tracer, final boolean forward) {
      return Props.create(TestActor.class, () -> new TestActor(tracer, forward));
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder().matchAny(x -> {
        final Span span = tracer.activeSpan();
        if (forward)
          getSender().forward(span == null, getContext());
        else
          getSender().tell(span == null, getSelf());
      }).build();
    }
  }
}