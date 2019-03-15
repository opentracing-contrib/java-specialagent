package io.opentracing.contrib.specialagent.asynchttpclient;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.mock.MockTracer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.util.HttpConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@Config
public class AsyncHttpClientTest {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testNoHandler(MockTracer tracer) {
    AsyncHttpClient client = new DefaultAsyncHttpClient();

    Request request = new RequestBuilder(HttpConstants.Methods.GET)
        .setUrl("http://localhost:12345")
        .build();

    try {
      client.executeRequest(request).get(10, TimeUnit.SECONDS);
    } catch (Exception ignore) {
      // OK
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(1));

    assertEquals(1, tracer.finishedSpans().size());
    assertNull(tracer.activeSpan());
  }

  @Test
  public void testWithHandler(MockTracer tracer) {

    AsyncHttpClient client = new DefaultAsyncHttpClient();

    Request request = new RequestBuilder(HttpConstants.Methods.GET)
        .setUrl("http://localhost:12345")
        .build();

    final AtomicInteger counter = new AtomicInteger();

    try {
      client.executeRequest(request, new AsyncCompletionHandler<Object>() {
        @Override
        public Object onCompleted(Response response) {
          assertNotNull(tracer.activeSpan());
          counter.incrementAndGet();
          return response;
        }

        @Override
        public void onThrowable(Throwable t) {
          assertNotNull(tracer.activeSpan());
          counter.incrementAndGet();
        }
      }).get(10, TimeUnit.SECONDS);
    } catch (Exception ignore) {
      // OK
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(1));

    assertEquals(1, tracer.finishedSpans().size());
    assertEquals(1, counter.get());
    assertNull(tracer.activeSpan());
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
