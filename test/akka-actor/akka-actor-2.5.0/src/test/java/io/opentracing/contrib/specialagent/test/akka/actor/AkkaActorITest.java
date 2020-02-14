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

package io.opentracing.contrib.specialagent.test.akka.actor;

import static akka.pattern.Patterns.*;
import static org.junit.Assert.*;

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import java.util.concurrent.CountDownLatch;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.util.GlobalTracer;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class AkkaActorITest {
  public static void main(final String[] args) throws Exception {
    final ActorSystem system = ActorSystem.create();

    testAsk(system);
    testTell(system);
    testForward(system);

    Await.result(system.terminate(), getDefaultDuration());
  }

  private static void testAsk(final ActorSystem system) throws Exception {
    final ActorRef actorRef = system.actorOf(TestActor.props(false), "ask");
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(2);

    final Future<Object> future = ask(actorRef, "ask", new Timeout(getDefaultDuration()));
    final Boolean isSpanNull = (Boolean)Await.result(future, getDefaultDuration());
    assertFalse(isSpanNull);
    TestUtil.checkSpan(latch, new ComponentSpanCount("java-akka", 2, true));
  }

  private static void testTell(final ActorSystem system) throws Exception {
    final ActorRef actorRef = system.actorOf(TestActor.props(false), "tell");
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(4);

    actorRef.tell("tell", ActorRef.noSender());

    final ActorSelection actorSelection = system.actorSelection(actorRef.path());
    actorSelection.tell("tell-selection", ActorRef.noSender());

    TestUtil.checkSpan(latch, new ComponentSpanCount("java-akka", 4));
  }

  private static void testForward(final ActorSystem system) throws Exception {
    final ActorRef actorRef = system.actorOf(TestActor.props(true), "forward");
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(2);

    final Future<Object> future = ask(actorRef, "forward", new Timeout(getDefaultDuration()));
    final Boolean isSpanNull = (Boolean)Await.result(future, getDefaultDuration());
    assertFalse(isSpanNull);

    TestUtil.checkSpan(latch, new ComponentSpanCount("java-akka", 2, true));
  }

  private static FiniteDuration getDefaultDuration() {
    return Duration.create(15, "seconds");
  }

  static class TestActor extends AbstractActor {
    private final boolean forward;

    TestActor(final boolean forward) {
      this.forward = forward;
    }

    static Props props(final boolean forward) {
      return Props.create(TestActor.class, () -> new TestActor(forward));
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder().matchAny(x -> {
        final Span span = GlobalTracer.get().activeSpan();
        if (forward)
          getSender().forward(span == null, getContext());
        else
          getSender().tell(span == null, getSelf());
      }).build();
    }
  }
}