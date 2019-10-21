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

package concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.Test;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class ConcurrentITest {
  public static void main(final String[] args) throws ExecutionException, InterruptedException {
    new ConcurrentITest().test();
  }

  @Test
  public void test() throws ExecutionException, InterruptedException {
    final Span parent = GlobalTracer.get().buildSpan("parent")
      .withTag(Tags.COMPONENT, "parent").start();

    testExecutor(parent);
    testThread(parent);
    //testParallelStream(parent);

    parent.finish();
    TestUtil.checkSpan("parent", 1);
  }

  private static void testExecutor(final Span parent) throws ExecutionException, InterruptedException {
    final ExecutorService service = Executors.newFixedThreadPool(10);
    try (final Scope scope = GlobalTracer.get().activateSpan(parent)) {
      service.submit(new Runnable() {
        @Override
        public void run() {
          System.out.println("Active span: " + GlobalTracer.get().activeSpan());
          if (GlobalTracer.get().activeSpan() == null)
            throw new AssertionError("ERROR: no active span");
        }
      }).get();
    }
  }

  private static void testThread(final Span parent) throws InterruptedException {
    final Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        System.out.println("Active span: " + GlobalTracer.get().activeSpan());
        if (GlobalTracer.get().activeSpan() == null)
          throw new AssertionError("ERROR: no active span");
      }
    });

    try (final Scope scope = GlobalTracer.get().activateSpan(parent)) {
      thread.start();
    }

    thread.join();
  }

  private static void testParallelStream(final Span parent) {
    try (final Scope scope = GlobalTracer.get().activateSpan(parent)) {
      final int sum = IntStream.range(1, 10)
        .parallel()
        .map(operand -> {
          // TODO: here should be active span
          System.out.println("Thread: " + Thread.currentThread().getName() +
              " Active span: " + GlobalTracer.get().activeSpan());
          return operand * 2;
        }).sum();

      System.out.println("Sum: " + sum);
    }
  }
}