package io.opentracing.contrib.specialagent.okhttp;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.ByteBuddyAgent;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

//@RunWith(AgentRunner.class)
//@AgentRunner.Config(debug=true, verbose=true, transformer=AgentRunner.Transformer.BYTEBUDDY)
public class OkHttpByteBuddyTest {
  public static MockTracer tracer = new MockTracer();

  static {
    try {
      GlobalTracer.register(tracer);
      AgentPlugin.premain(null, ByteBuddyAgent.install());
    }
    catch (final Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Test
  public void test() throws Exception {
    final OkHttpClient client = new OkHttpClient.Builder().build();
    try (final MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("hello, world!").setResponseCode(200));

      final HttpUrl httpUrl = server.url("/hello");

      final Request request = new Request.Builder().url(httpUrl).build();
      final Response response = client.newCall(request).execute();
      assertEquals(200, response.code());

      final List<MockSpan> finishedSpans = tracer.finishedSpans();
      assertEquals(2, finishedSpans.size());
      assertEquals("GET", finishedSpans.get(0).operationName());
      assertEquals("GET", finishedSpans.get(1).operationName());
    }
  }
}