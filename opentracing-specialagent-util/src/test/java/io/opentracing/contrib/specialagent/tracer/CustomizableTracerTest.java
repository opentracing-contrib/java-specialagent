package io.opentracing.contrib.specialagent.tracer;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(Parameterized.class)
public class CustomizableTracerTest {
  private static List<URL> getResourceFiles(final String path) throws IOException {
    final List<URL> resources = new ArrayList<>();
    final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream(path)))) {
      for (String fileName; (fileName = reader.readLine()) != null;)
        resources.add(classLoader.getResource(path + "/" + fileName));
    }

    return resources;
  }

  @Parameterized.Parameters(name = "{0}")
  public static URL[] data() throws IOException {
    return getResourceFiles("spanCustomizer").toArray(new URL[0]);
  }

  private final JsonObject root;

  public CustomizableTracerTest(final URL resource) throws IOException, JsonParserException {
    try (final InputStream in = resource.openStream()) {
      root = JsonParser.object().from(in);
    }
  }

  @Test
  public void test() {
    final JsonArray jsonRules = Objects.requireNonNull(root.getArray("rules"));
    final String expectedError = root.getString("expectedError");
    if (expectedError != null) {
      try {
        parseRules(jsonRules);
        Assert.fail("no exception thrown");
      }
      catch (final IllegalStateException e) {
        assertEquals(expectedError, e.getMessage());
      }
    }
    else {
      playScenario(root, parseRules(jsonRules));
    }
  }

  private static SpanRules parseRules(final JsonArray jsonRules) {
    return new SpanRules(SpanRuleParser.parseRules(jsonRules, "test: "));
  }

  private static void playScenario(final JsonObject root, final SpanRules rules) {
    final MockTracer mockTracer = new MockTracer();
    final CustomizableTracerScenario scenario = CustomizableTracerScenario.fromString(root.getString("scenario"));
    scenario.play(new CustomizableTracer(mockTracer, rules));

    final JsonArray expectedSpans = Objects.requireNonNull(root.getArray("expectedSpans"));

    final List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(expectedSpans.size(), spans.size());
    for (int i = 0; i < spans.size(); ++i) {
      final MockSpan span = spans.get(i);
      final String message = "span " + i;
      final JsonObject expectedSpan = Objects.requireNonNull(expectedSpans.getObject(i), message);

      assertEquals(message, Objects.requireNonNull(expectedSpan.getString("operationName")), span.operationName());
      assertTags(expectedSpan, span, message);
      assertLogs(expectedSpan, span, message);
    }
  }

  private static void assertTags(final JsonObject expectedSpan, final MockSpan span, final String message) {
    final JsonObject expectedTags = expectedSpan.getObject("tags");
    final Map<String,Object> tags = span.tags();
    if (expectedTags == null) {
      assertEquals(message, 0, tags.size());
      return;
    }

    for (final Map.Entry<String,Object> entry : expectedTags.entrySet()) {
      final String key = entry.getKey();
      assertEquals(message + " tag " + key, entry.getValue(), tags.get(key));
    }

    assertEquals(message, expectedTags.size(), tags.size());
  }

  private static void assertLogs(final JsonObject expectedSpan, final MockSpan span, final String message) {
    final JsonArray expectedLogs = expectedSpan.getArray("logs");
    final List<MockSpan.LogEntry> logEntries = span.logEntries();
    if (expectedLogs == null) {
      assertEquals(0, logEntries.size());
      return;
    }

    assertEquals(message, expectedLogs.size(), logEntries.size());
    for (int i = 0; i < expectedLogs.size(); ++i) {
      final JsonObject expectedLog = Objects.requireNonNull(expectedLogs.getObject(i));
      assertLog(expectedLog, logEntries.get(i), message + " log " + i);
    }
  }

  private static void assertLog(final JsonObject expectedLog, final MockSpan.LogEntry logEntry, final String message) {
    final Map<String,?> fields = logEntry.fields();

    final String expectedEvent = expectedLog.getString("event");
    final JsonObject expectedFields = expectedLog.getObject("fields");

    final Number number = expectedLog.getNumber("timestampMicros");
    if (number != null)
      assertEquals(message, number.longValue(), logEntry.timestampMicros());

    int given = 0;
    if (expectedEvent != null) {
      ++given;

      assertEquals(message, 1, fields.size());
      assertEquals(message, expectedEvent, fields.get("event"));
    }

    if (expectedFields != null) {
      ++given;
      assertEquals(message, expectedFields.size(), fields.size());
      for (final Map.Entry<String,Object> entry : expectedFields.entrySet()) {
        final String key = entry.getKey();
        assertEquals(message + " key " + key, entry.getValue(), fields.get(key));
      }
    }

    assertEquals(message, 1, given);
  }
}