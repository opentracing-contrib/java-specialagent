package io.opentracing.contrib.specialagent.tracer;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CustomizableTracerTest {

  private final File file;
  private final List<CustomizableSpanBuilder> spanBuilders = new ArrayList<>();
  private final List<OperationNameCustomizer> operationNameCustomizers = new ArrayList<>();
  private final List<LogFieldCustomizer> logFieldCustomizers = new ArrayList<>();
  private int regexMatches = 0;

  @Parameterized.Parameters(name = "{1}")
  public static Collection<Object[]> data() {
    Collection<Object[]> result = new ArrayList<>();
    for (File file : new File("src/test/resources/spanCustomizer").listFiles()) {
      result.add(new Object[]{file, file.getName()});
    }
    return result;
  }

  public CustomizableTracerTest(File file, String name) {
    this.file = file;
  }

  @Test
  public void test() throws Exception {
    JsonObject root;
    try (FileInputStream stream = new FileInputStream(file)) {
      root = JsonParser.object().from(stream);
    }

    JsonArray jsonRules = Objects.requireNonNull(root.getArray("rules"));

    String expectedError = root.getString("expectedError");
    if (expectedError != null) {
      try {
        parseRules(jsonRules);
      } catch (IllegalStateException e) {
        assertEquals(expectedError, e.getMessage());
        return;
      }
      Assert.fail("no exception thrown");
    } else {
      playScenario(root, parseRules(jsonRules));
    }
  }

  private SpanRules parseRules(JsonArray jsonRules) {
    SpanRuleParser parser = new SpanRuleParser() {
      @Override
      protected RegexSpanRuleMatcher regexSpanRuleMatcher(String valueRegex) {
        return new RegexSpanRuleMatcher(valueRegex) {
          @Override
          public SpanRuleMatch match(Object value) {
            regexMatches++;
            return super.match(value);
          }
        };
      }
    };
    return new SpanRules(parser.parseRules(jsonRules, "test: "));
  }

  private void playScenario(JsonObject root, SpanRules rules) {
    MockTracer mockTracer = new MockTracer();

    CustomizableTracerScenario scenario = CustomizableTracerScenario.valueOf(root.getString("scenario"));
    scenario.play(spyTracer(new CustomizableTracer(mockTracer, rules)));

    JsonArray expectedSpans = Objects.requireNonNull(root.getArray("expectedSpans"));

    List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(expectedSpans.size(), spans.size());
    for (int i = 0; i < spans.size(); i++) {
      MockSpan span = spans.get(i);
      String message = "span " + i;
      JsonObject expectedSpan = Objects.requireNonNull(expectedSpans.getObject(i), message);

      assertEquals(message, Objects.requireNonNull(expectedSpan.getString("operationName")), span.operationName());
      assertTags(expectedSpan, span, message);
      assertLogs(expectedSpan, span, message);
    }

    assertAllocations(root);
  }

  private CustomizableTracer spyTracer(CustomizableTracer tracer) {
    if (true) {
//      return tracer;
    }
    CustomizableTracer spy = Mockito.spy(tracer);

    Mockito.when(spy.operationNameCustomizer(Mockito.anyString())).thenAnswer(new Answer<OperationNameCustomizer>() {
      @Override
      public OperationNameCustomizer answer(InvocationOnMock invocationOnMock) throws Throwable {
        OperationNameCustomizer customizer = (OperationNameCustomizer) invocationOnMock.callRealMethod();
        operationNameCustomizers.add(customizer);
        return customizer;
      }
    });

    Mockito.when(spy.buildSpan(Mockito.anyString())).thenAnswer(new Answer<Tracer.SpanBuilder>() {
      @Override
      public Tracer.SpanBuilder answer(InvocationOnMock invocationOnMock) throws Throwable {
        return spyBuilder((CustomizableSpanBuilder) invocationOnMock.callRealMethod());
      }
    });
    return spy;
  }

  private Tracer.SpanBuilder spyBuilder(CustomizableSpanBuilder builder) {
    spanBuilders.add(builder);

    CustomizableSpanBuilder spy = Mockito.spy(builder);
    Mockito.when(spy.start()).thenAnswer(new Answer<Span>() {
      @Override
      public Span answer(InvocationOnMock invocationOnMock) throws Throwable {
        return spySpan((CustomizableSpan) invocationOnMock.callRealMethod());
      }
    });

    return spy;
  }

  private Span spySpan(CustomizableSpan span) {
    CustomizableSpan spy = Mockito.spy(span);

    Mockito.when(spy.logFieldCustomizer(Mockito.anyLong())).thenAnswer(new Answer<LogFieldCustomizer>() {
      @Override
      public LogFieldCustomizer answer(InvocationOnMock invocationOnMock) throws Throwable {
        LogFieldCustomizer customizer = (LogFieldCustomizer) invocationOnMock.callRealMethod();
        logFieldCustomizers.add(customizer);
        return customizer;
      }
    });

    return spy;
  }

  private void assertTags(JsonObject expectedSpan, MockSpan span, String message) {
    JsonObject expectedTags = expectedSpan.getObject("tags");
    Map<String, Object> tags = span.tags();

    if (expectedTags == null) {
      assertEquals(message, 0, tags.size());
      return;
    }

    for (Map.Entry<String, Object> entry : expectedTags.entrySet()) {
      String key = entry.getKey();
      assertEquals(message + " tag " + key, entry.getValue(), tags.get(key));
    }
    assertEquals(message, expectedTags.size(), tags.size());
  }

  private void assertLogs(JsonObject expectedSpan, MockSpan span, String message) {
    JsonArray expectedLogs = expectedSpan.getArray("logs");
    List<MockSpan.LogEntry> logEntries = span.logEntries();

    if (expectedLogs == null) {
      assertEquals(0, logEntries.size());
      return;
    }

    assertEquals(message, expectedLogs.size(), logEntries.size());

    for (int i = 0; i < expectedLogs.size(); i++) {
      JsonObject expectedLog = Objects.requireNonNull(expectedLogs.getObject(i));
      assertLog(expectedLog, logEntries.get(i), message + " log " + i);
    }
  }

  private void assertLog(JsonObject expectedLog, MockSpan.LogEntry logEntry, String message) {
    Map<String, ?> fields = logEntry.fields();

    String expectedEvent = expectedLog.getString("event");
    JsonObject expectedFields = expectedLog.getObject("fields");

    Number number = expectedLog.getNumber("timestampMicros");
    if (number != null) {
      assertEquals(message, number.longValue(), logEntry.timestampMicros());
    }

    int given = 0;
    if (expectedEvent != null) {
      given++;

      assertEquals(message, 1, fields.size());
      assertEquals(message, expectedEvent, fields.get("event"));
    }
    if (expectedFields != null) {
      given++;

      assertEquals(message, expectedFields.size(), fields.size());
      for (Map.Entry<String, Object> entry : expectedFields.entrySet()) {
        String key = entry.getKey();
        assertEquals(message + " key " + key, entry.getValue(), fields.get(key));
      }
    }
    assertEquals(message, 1, given);
  }

  private void assertAllocations(JsonObject root) {
    int listAllocations = 0;
    int mapAllocations = 0;

    for (CustomizableSpanBuilder builder : spanBuilders) {
      if (builder.customizer.log != null) {
        listAllocations++;
      }
    }
    for (OperationNameCustomizer customizer : operationNameCustomizers) {
      //log is passed to CustomizableSpanBuilder and invocation is counted there
      if (customizer.tags != null) {
        mapAllocations++;
      }
    }
    for (LogFieldCustomizer customizer : logFieldCustomizers) {
      if (customizer.fields != null) {
        mapAllocations++;
      }
    }

    assertEquals(root.getNumber("expectedListAllocations", 0), listAllocations);
    assertEquals(root.getNumber("expectedMapAllocations", 0), mapAllocations);
    assertEquals(root.getNumber("expectedRegexMatches", 0), regexMatches);
  }
}
