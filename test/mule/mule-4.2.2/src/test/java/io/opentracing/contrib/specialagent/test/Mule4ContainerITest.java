package io.opentracing.contrib.specialagent.test;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.mule.runtime.api.util.MuleSystemProperties;
import org.mule.runtime.core.api.config.MuleProperties;
import org.mule.runtime.module.launcher.MuleContainer;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

public class Mule4ContainerITest {
  private static final String MULE_HOME = "/src/test/mule-home";

  public static void main(final String[] args) throws Exception {
    if (!System.getProperty("java.version").startsWith("1.8.")) {
      System.err.println("This test is not working: https://github.com/opentracing-contrib/java-specialagent/issues/405");
      return;
    }

    final String homeDir = new File("").getAbsolutePath() + MULE_HOME;
    final Mule4ContainerITest lock = new Mule4ContainerITest();
    System.setProperty(MuleSystemProperties.MULE_SIMPLE_LOG, "true");
    System.setProperty(MuleProperties.MULE_HOME_DIRECTORY_PROPERTY, homeDir);
    System.setProperty(MuleProperties.MULE_BASE_DIRECTORY_PROPERTY, homeDir);
    final MuleContainer container = new MuleContainer(new String[] {"-M-Dmule.verbose.exceptions=true", "-M-XX:-UseBiasedLocking", "-M-Dfile.encoding=UTF-8", "-M-Dmule.timeout.disable=false"});

    try {
      container.start(false);
      // giving the runtime time to make the http listener available
      synchronized (lock) {
        lock.wait(200);
      }

      testHttpFlow(lock);
      testDbFlow(lock);
    }
    finally {
      container.stop();
      System.clearProperty(MuleSystemProperties.MULE_SIMPLE_LOG);
      System.clearProperty(MuleProperties.MULE_HOME_DIRECTORY_PROPERTY);
      System.clearProperty(MuleProperties.MULE_BASE_DIRECTORY_PROPERTY);
    }
  }

  private static void testHttpFlow(Mule4ContainerITest lock) throws InterruptedException, IOException {
    final HttpURLConnection connection = (HttpURLConnection)new URL("http://localhost:8081/http").openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();
    connection.disconnect();

    // giving grizzly-http-server time to finish the span, since it's on a diff
    // thread
    synchronized (lock) {
      lock.wait(200);
    }

    if (200 != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    checkSpans("java-grizzly-http-server", "http:request", "java-grizzly-ahc", "mule:logger");
  }

  private static void testDbFlow(final Mule4ContainerITest lock) throws InterruptedException, IOException {
    final HttpURLConnection connection = (HttpURLConnection)new URL("http://localhost:8081/db").openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();
    connection.disconnect();

    // giving grizzly-http-server time to finish the span, since it's on a diff
    // thread
    synchronized (lock) {
      lock.wait(200);
    }

    if (200 != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    checkSpans("java-grizzly-http-server", "db:select", "java-jdbc");
  }

  private static void checkSpans(final String ... components) {
    final MockTracer mockTracer = (MockTracer)TestUtil.getGlobalTracer();
    System.out.println("Spans: " + mockTracer.finishedSpans());
    final Map<String,Set<MockSpan>> finishedSpans = mockTracer.finishedSpans().stream().peek(mockSpan -> {
      System.out.println("Span: " + mockSpan);
      System.out.println("\tComponent: " + mockSpan.tags().get(Tags.COMPONENT.getKey()));
      System.out.println("\tTags: " + mockSpan.tags());
      System.out.println("\tLogs: ");
      for (final MockSpan.LogEntry logEntry : mockSpan.logEntries())
        System.out.println("\t" + logEntry.fields());
    }).collect(Collectors.groupingBy(mockSpan -> (String)mockSpan.tags().get(Tags.COMPONENT.getKey()), Collectors.toSet()));

    for (final String component : components)
      if (!finishedSpans.containsKey(component))
        throw new AssertionError("ERROR: " + component + " span not found");

    mockTracer.reset();
  }
}