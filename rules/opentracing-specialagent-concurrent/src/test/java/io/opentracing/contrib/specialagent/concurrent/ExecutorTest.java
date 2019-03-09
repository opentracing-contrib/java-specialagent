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

package io.opentracing.contrib.specialagent.concurrent;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.Manager.Event;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(events=Event.ERROR)
public class ExecutorTest extends AbstractConcurrentTest {
  @Test
	public void testExecute(final MockTracer tracer) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final Executor executor = Executors.newFixedThreadPool(10);

    final MockSpan parentSpan = tracer.buildSpan("foo").startManual();
		tracer.scopeManager().activate(parentSpan, true);
		executor.execute(new TestRunnable(tracer, countDownLatch));

		countDownLatch.await();
		assertParentSpan(tracer, parentSpan);
		assertEquals(1, tracer.finishedSpans().size());
	}
}