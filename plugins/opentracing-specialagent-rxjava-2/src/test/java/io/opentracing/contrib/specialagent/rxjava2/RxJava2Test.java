package io.opentracing.contrib.specialagent.rxjava;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config
public class RxJava2Test {

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void observerTest(MockTracer tracer) {
    List<Integer> result = new ArrayList<>();
    executeSequentialObservable("sequential", result, tracer);

    assertEquals(5, result.size());

    List<MockSpan> spans = tracer.finishedSpans();
    System.out.println(spans);
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest(MockTracer tracer) {
    Observable<Integer> observable = createSequentialObservable(tracer);
    List<Integer> result = new ArrayList<>();
    Consumer<Integer> onNext = consumer(result);

    final Disposable disposable = observable.subscribe(onNext);
    System.out.println(disposable);

    assertEquals(5, result.size());

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest2(MockTracer tracer) {
    Observable<Integer> observable = createSequentialObservable(tracer);
    List<Integer> result = new ArrayList<>();
    Consumer<Integer> onNext = consumer(result);
    observable.subscribe(onNext, onError());

    assertEquals(5, result.size());

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest3(MockTracer tracer) {
    Observable<Integer> observable = createSequentialObservable(tracer);
    List<Integer> result = new ArrayList<>();
    Consumer<Integer> onNext = consumer(result);
    observable.subscribe(onNext, onError(), onComplete());

    assertEquals(5, result.size());

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  @Test
  public void consumerTest4(MockTracer tracer) {
    Observable<Integer> observable = createSequentialObservable(tracer);
    List<Integer> result = new ArrayList<>();
    Consumer<Integer> onNext = consumer(result);
    Consumer onSubscribe = new Consumer() {
      @Override
      public void accept(Object o) {

      }
    };

    observable.subscribe(onNext, onError(), onComplete(), onSubscribe);

    assertEquals(5, result.size());

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());

    assertNull(tracer.scopeManager().active());
  }

  private Consumer<Throwable> onError() {
    return new Consumer<Throwable>() {
      @Override
      public void accept(Throwable throwable) {
      }
    };
  }

  private Action onComplete() {
    return new Action() {
      @Override
      public void run() {
      }
    };
  }

  private <T> Consumer<T> consumer(final List<T> result) {
    return new Consumer<T>() {
      @Override
      public void accept(T value) {
        System.out.println(value);
        result.add(value);
      }
    };
  }

  private void executeSequentialObservable(String name, List<Integer> result, MockTracer tracer) {
    Observable<Integer> observable = createSequentialObservable(tracer);
    Observer<Integer> observer = observer(name, result);
    observable.subscribe(observer);
  }

  private static Observable<Integer> createSequentialObservable(final MockTracer tracer) {
    return Observable.range(1, 10)
        .map(new Function<Integer, Integer>() {
          @Override
          public Integer apply(Integer integer) {
            System.out.println(tracer.scopeManager().active() + ": " + integer);
            assertNotNull(tracer.scopeManager().active());
            return integer * 3;
          }
        })
        .filter(new Predicate<Integer>() {
          @Override
          public boolean test(Integer integer) {
            assertNotNull(tracer.scopeManager().active());
            return integer % 2 == 0;
          }
        });
  }

  private static <T> Observer<T> observer(final String name, final List<T> result) {
    return new Observer<T>() {
      @Override
      public void onSubscribe(Disposable d) {
      }

      @Override
      public void onNext(T value) {
        System.out.println(name + ": " + value);
        result.add(value);
      }

      @Override
      public void onError(Throwable e) {
        e.printStackTrace();
      }

      @Override
      public void onComplete() {
        System.out.println(name + ": onComplete");
      }
    };
  }

}
