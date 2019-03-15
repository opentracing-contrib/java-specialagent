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

package io.opentracing.contrib.specialagent.rxjava2;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

@RunWith(AgentRunner.class)
public class RxJava2Test {
  private static final Logger logger = Logger.getLogger(RxJava2Test.class.getName());
  private static final String COMPLETED = "completed";

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void observerTest(final MockTracer tracer) {
    final List<Integer> result = new ArrayList<>();
    executeSequentialObservable("sequential", result, tracer);

    assertEquals(5, result.size());

    final List<MockSpan> spans = tracer.finishedSpans();
    logger.fine(String.valueOf(spans));
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest(final MockTracer tracer) {
    final Observable<Integer> observable = createSequentialObservable(tracer, false);
    final List<Integer> result = new ArrayList<>();
    final Consumer<Integer> onNext = consumer(result);

    final Disposable disposable = observable.subscribe(onNext);
    logger.fine(String.valueOf(disposable));

    assertEquals(5, result.size());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest2(final MockTracer tracer) {
    final Observable<Integer> observable = createSequentialObservable(tracer, false);
    final List<Integer> result = new ArrayList<>();
    final Consumer<Integer> onNext = consumer(result);
    final List<String> errorList = new ArrayList<>();
    observable.subscribe(onNext, onError(errorList));

    assertEquals(5, result.size());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertTrue(errorList.isEmpty());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest3(final MockTracer tracer) {
    final Observable<Integer> observable = createSequentialObservable(tracer, false);
    final List<Integer> result = new ArrayList<>();
    final Consumer<Integer> onNext = consumer(result);
    final List<String> completeList = new ArrayList<>();
    final List<String> errorList = new ArrayList<>();
    observable.subscribe(onNext, onError(errorList), onComplete(completeList, tracer));

    assertEquals(5, result.size());
    assertTrue(completeList.contains(COMPLETED));
    assertEquals(1, completeList.size());
    assertTrue(errorList.isEmpty());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest3WithError(final MockTracer tracer) {
    final Observable<Integer> observable = createSequentialObservable(tracer, true);
    final List<Integer> result = new ArrayList<>();
    final Consumer<Integer> onNext = consumer(result);
    final List<String> completeList = new ArrayList<>();
    final List<String> errorList = new ArrayList<>();
    observable.subscribe(onNext, onError(errorList), onComplete(completeList, tracer));

    assertEquals(0, result.size());
    assertEquals(0, completeList.size());
    assertEquals(1, errorList.size());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest4(final MockTracer tracer) {
    final Observable<Integer> observable = createSequentialObservable(tracer, false);
    final List<Integer> result = new ArrayList<>();
    final Consumer<Integer> onNext = consumer(result);
    final List<String> subscribeList = new ArrayList<>();
    final String subscribed = "subscribed";
    final Consumer<Object> onSubscribe = new Consumer<Object>() {
      @Override
      public void accept(final Object t) {
        subscribeList.add(subscribed);
      }
    };

    final List<String> completeList = new ArrayList<>();
    final List<String> errorList = new ArrayList<>();
    observable.subscribe(onNext, onError(errorList), onComplete(completeList, tracer), onSubscribe);

    assertEquals(5, result.size());

    assertEquals(1, completeList.size());
    assertTrue(completeList.contains(COMPLETED));

    assertEquals(1, subscribeList.size());
    assertTrue(subscribeList.contains(subscribed));

    assertTrue(errorList.isEmpty());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest4WithError(final MockTracer tracer) {
    final Observable<Integer> observable = createSequentialObservable(tracer, true);
    final List<Integer> result = new ArrayList<>();
    final Consumer<Integer> onNext = consumer(result);
    final List<String> subscribeList = new ArrayList<>();
    final Consumer<Object> onSubscribe = new Consumer<Object>() {
      @Override
      public void accept(final Object t) {
        subscribeList.add("subscribed");
      }
    };

    final List<String> completeList = new ArrayList<>();
    final List<String> errorList = new ArrayList<>();
    observable.subscribe(onNext, onError(errorList), onComplete(completeList, tracer), onSubscribe);

    assertEquals(0, result.size());
    assertEquals(0, completeList.size());
    assertEquals(1, subscribeList.size());
    assertEquals(1, errorList.size());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  private static <T>Consumer<T> consumer(final List<T> result) {
    return new Consumer<T>() {
      @Override
      public void accept(final T t) {
        logger.fine(String.valueOf(t));
        result.add(t);
      }
    };
  }

  private static Consumer<Throwable> onError(final List<String> errorList) {
    return new Consumer<Throwable>() {
      @Override
      public void accept(final Throwable t) {
        errorList.add("error");
      }
    };
  }

  private static Action onComplete(final List<String> completeList, final MockTracer tracer) {
    return new Action() {
      @Override
      public void run() {
        logger.fine("onComplete()");
        assertNotNull(tracer.scopeManager().active());
        completeList.add(COMPLETED);
      }
    };
  }

  private static void executeSequentialObservable(final String name, final List<Integer> result, final MockTracer tracer) {
    final Observable<Integer> observable = createSequentialObservable(tracer, false);
    final Observer<Integer> observer = observer(name, result);
    observable.subscribe(observer);
  }

  private static Observable<Integer> createSequentialObservable(final MockTracer tracer, final boolean withError) {
    return Observable.range(1, 10).map(new Function<Integer,Integer>() {
      @Override
      public Integer apply(final Integer t) {
        logger.fine(tracer.scopeManager().active() + ": " + t);
        assertNotNull(tracer.scopeManager().active());
        return t * 3;
      }
    }).filter(new Predicate<Integer>() {
      @Override
      public boolean test(final Integer t) {
        assertNotNull(tracer.scopeManager().active());
        if (withError)
          throw new IllegalStateException();

        return t % 2 == 0;
      }
    });
  }

  private static <T>Observer<T> observer(final String name, final List<T> result) {
    return new Observer<T>() {
      @Override
      public void onSubscribe(final Disposable d) {
      }

      @Override
      public void onNext(final T next) {
        logger.fine(name + ": " + next);
        result.add(next);
      }

      @Override
      public void onError(final Throwable e) {
        e.printStackTrace();
      }

      @Override
      public void onComplete() {
        logger.fine(name + ": onComplete");
      }
    };
  }
}