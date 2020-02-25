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

package io.opentracing.contrib.specialagent.test.concurrent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.IntStream;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class ConcurrentITest {
  public static void main(final String[] args) throws ExecutionException, InterruptedException {
    final Span parent = GlobalTracer.get()
      .buildSpan("parent")
      .withTag(Tags.COMPONENT, "parent")
      .start();

    testThread(parent);
    testRunnable(parent);
    testExecutor(parent);
    testParallelStream(parent);
    testForkJoinPool(parent);

    parent.finish();
    TestUtil.checkSpan(new ComponentSpanCount("parent", 1));
  }

  private static void run() {
    System.out.println("Active span: " + GlobalTracer.get().activeSpan());
    if (GlobalTracer.get().activeSpan() == null)
      throw new AssertionError("ERROR: no active span");
  }

  private static void testThread(final Span parent) throws InterruptedException {
    final Thread thread = new Thread() {
      @Override
      public void run() {
        ConcurrentITest.run();
      }
    };

    try (final Scope scope = GlobalTracer.get().activateSpan(parent)) {
      thread.start();
    }

    thread.join();
  }

  private static void testRunnable(final Span parent) throws InterruptedException {
    final Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        ConcurrentITest.run();
      }
    });

    try (final Scope scope = GlobalTracer.get().activateSpan(parent)) {
      thread.start();
    }

    thread.join();
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

          service.shutdown();
        }
      }).get();
    }
  }

  private static void testParallelStream(final Span parent) {
    try (final Scope scope = GlobalTracer.get().activateSpan(parent)) {
      final int sum = IntStream.range(1, 10).parallel().map(operand -> {
        System.out.println("Active span: " + GlobalTracer.get().activeSpan());
        if (GlobalTracer.get().activeSpan() == null)
          throw new AssertionError("ERROR: no active span");

        return operand * 2;
      }).sum();

      System.out.println("Sum: " + sum);
    }
  }

  private static void testForkJoinPool(final Span parent) {
    final ForkJoinPool forkJoinPool = new ForkJoinPool(2);
    final ForkJoinRecursiveTask forkJoinRecursiveTask = new ForkJoinRecursiveTask(IntStream.range(1, 10).toArray());

    try (final Scope scope = GlobalTracer.get().activateSpan(parent)) {
      forkJoinPool.execute(forkJoinRecursiveTask);
    }

    final int result = forkJoinRecursiveTask.join();
    if (result != 450)
      throw new AssertionError("ERROR: wrong fork join result: " + result);
  }

  private static class ForkJoinRecursiveTask extends RecursiveTask<Integer> {
    private static final long serialVersionUID = -5112698420453150198L;

    private static Integer compute(final int[] array) {
      TestUtil.checkActiveSpan();
      return Arrays.stream(array).map(a -> a * 10).sum();
    }

    private final int[] array;

    public ForkJoinRecursiveTask(final int[] array) {
      this.array = array;
    }

    @Override
    protected Integer compute() {
      TestUtil.checkActiveSpan();
      if (array.length > 2)
        return ForkJoinTask.invokeAll(createSubtasks()).stream().mapToInt(ForkJoinTask::join).sum();

      return compute(array);
    }

    private Collection<ForkJoinRecursiveTask> createSubtasks() {
      final List<ForkJoinRecursiveTask> dividedTasks = new ArrayList<>();
      dividedTasks.add(new ForkJoinRecursiveTask(Arrays.copyOfRange(array, 0, array.length / 2)));
      dividedTasks.add(new ForkJoinRecursiveTask(Arrays.copyOfRange(array, array.length / 2, array.length)));
      return dividedTasks;
    }
  }
}