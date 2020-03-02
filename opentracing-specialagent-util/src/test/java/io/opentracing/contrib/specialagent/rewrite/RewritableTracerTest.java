package io.opentracing.contrib.specialagent.rewrite;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
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

import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(Parameterized.class)
public class RewritableTracerTest {
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
    return getResourceFiles("rewrite").toArray(new URL[0]);
  }

  private final String fileName;
  private final JsonObject root;
  private final List<RewritableSpanBuilder> spanBuilders = new ArrayList<>();
  private final List<LogFieldRewriter> logFieldRewriters = new ArrayList<>();
  private int matches = 0;

  public RewritableTracerTest(final URL resource) throws IOException, JsonParserException, URISyntaxException {
    this.fileName = new File(resource.toURI()).getName();
    System.out.println(fileName);
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
      catch (final IllegalStateException | NullPointerException e) {
        assertEquals(fileName, expectedError, e.getMessage());
      }
    }
    else {
      playScenario(root, parseRules(jsonRules));
    }
  }

  private RewriteRules parseRules(final JsonArray jsonRules) {
    final RewriteRules rules = RewriteRules.parseRules(jsonRules, "test");
    for (final Map.Entry<String,List<RewriteRule>> entry : rules.keyToRules.entrySet()) {
      final List<RewriteRule> list = entry.getValue();
      for (int i = 0; i < list.size(); ++i) {
        final RewriteRule rule = list.get(i);
        list.set(i, new RewriteRule(rule.input, rule.outputs) {
          @Override
          Object rewriteValue(final Object matcher, final Object input, final Object output) {
            final Object out = super.rewriteValue(matcher, input, output);
            if (output != null && out != output && !String.valueOf(out).equals(String.valueOf(output)))
              ++matches;

            return out;
          }
        });
      }
    }

    return rules;
  }

  private void playScenario(final JsonObject root, final RewriteRules rules) {
    final MockTracer mockTracer = new MockTracer();

    final RewritableTracerScenario scenario = RewritableTracerScenario.fromString(root.getString("scenario"));
    scenario.play(getTracer(rules, mockTracer));

    final JsonArray expectedSpans = Objects.requireNonNull(root.getArray("expectedSpans"));

    final List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(fileName, expectedSpans.size(), spans.size());
    for (int i = 0; i < spans.size(); ++i) {
      final MockSpan span = spans.get(i);
      final String message = fileName + ": span " + i;
      final JsonObject expectedSpan = Objects.requireNonNull(expectedSpans.getObject(i), message);

      assertEquals(message, Objects.requireNonNull(expectedSpan.getString("operationName")), span.operationName());
      assertTags(expectedSpan, span, message);
      assertLogs(expectedSpan, span, message);
    }

    assertAllocations(root);
  }

  private RewritableTracer getTracer(final RewriteRules rules, final MockTracer mockTracer) {
    return new RewritableTracer(mockTracer, rules) {
      @Override
      public SpanBuilder buildSpan(final String operationName) {
        final RewritableSpanBuilder builder = new RewritableSpanBuilder(operationName, target.buildSpan(operationName), rules) {
          @Override
          protected RewritableSpan newRewritableSpan(final Span span) {
            return new RewritableSpan(span, rules) {
              @Override
              LogFieldRewriter newLogFieldRewriter() {
                final LogFieldRewriter logFieldRewriter = super.newLogFieldRewriter();
                logFieldRewriters.add(logFieldRewriter);
                return logFieldRewriter;
              }
            };
          }
        };
        spanBuilders.add(builder);
        return builder;
      }
    };
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
      assertEquals(message, 0, logEntries.size());
      return;
    }

    assertEquals(message, expectedLogs.size(), logEntries.size());
    for (int i = 0; i < expectedLogs.size(); ++i)
      assertLog(Objects.requireNonNull(expectedLogs.getObject(i)), logEntries.get(i), message + " log " + i);
  }

  private static void assertLog(final JsonObject expectedLog, final MockSpan.LogEntry logEntry, final String message) {
    final Map<String,?> fields = logEntry.fields();

    final String expectedEvent = expectedLog.getString("event");
    final JsonObject expectedFields = expectedLog.getObject("fields");

    final Number number = expectedLog.getNumber("timestampMicros");
    if (number != null)
      assertEquals(message, number.longValue(), logEntry.timestampMicros());
    else
      assertTrue(message, logEntry.timestampMicros() > 0);

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

  private void assertAllocations(final JsonObject root) {
    int listAllocations = 0;
    int mapAllocations = 0;
    for (final RewritableSpanBuilder builder : spanBuilders)
      if (builder.getLog() != null)
        ++listAllocations;

    for (final LogFieldRewriter rewriter : logFieldRewriters)
      if (rewriter.getFields() != null)
        ++mapAllocations;

    assertEquals(fileName + ": listAllocations", root.getNumber("expectedListAllocations", 0), listAllocations);
    assertEquals(fileName + ": mapAllocations", root.getNumber("expectedMapAllocations", 0), mapAllocations);
    assertEquals(fileName + ": matches", root.getNumber("expectedMatches", 0), matches);
  }
}