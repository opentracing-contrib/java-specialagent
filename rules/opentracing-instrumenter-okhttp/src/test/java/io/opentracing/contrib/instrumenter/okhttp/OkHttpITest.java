package io.opentracing.contrib.instrumenter.okhttp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.instrumenter.InstrumenterRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@RunWith(InstrumenterRunner.class)
@InstrumenterRunner.Debug(true)
public class OkHttpITest {
  private static final Logger logger = Logger.getLogger(OkHttpITest.class.getName());

  @Test
  public void test(final MockTracer tracer) throws IOException {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("getTracer(): " + tracer.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(tracer)));
      logger.fine("  ClassLoader: " + tracer.getClass().getClassLoader() + " " + ClassLoader.getSystemClassLoader().getResource(tracer.getClass().getName().replace('.', '/').concat(".class")));
      logger.fine("  GlobalTracer ClassLoader: " + GlobalTracer.class.getClassLoader() + " " + ClassLoader.getSystemClassLoader().getResource(GlobalTracer.class.getName().replace('.', '/').concat(".class")));
      logger.fine(MockWebServer.class.getClassLoader() + " " + MockWebServer.class.getProtectionDomain().getCodeSource().getLocation());
      logger.fine(okhttp3.Interceptor.class.getClassLoader() + " " + okhttp3.Interceptor.class.getProtectionDomain().getCodeSource().getLocation());
    }

    assertEquals(URLClassLoader.class, MockWebServer.class.getClassLoader().getClass());
    try (final MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("hello, world!").setResponseCode(200));

      final HttpUrl httpUrl = server.url("/hello");

      assertEquals(URLClassLoader.class, Interceptor.class.getClassLoader().getClass());
      assertEquals(URLClassLoader.class, OkHttpClient.class.getClassLoader().getClass());
      assertEquals(URLClassLoader.class, OkHttpClient.Builder.class.getClassLoader().getClass());
      assertEquals(URLClassLoader.class, OkHttpClient.class.getClassLoader().getClass());

      // TODO: Rule does not currently work when just using the OkHttpClient
      // default constructor
      final OkHttpClient client = new OkHttpClient.Builder().build();

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