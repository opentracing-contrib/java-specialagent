package io.opentracing.contrib.specialagent.adaption;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

public class AdaptionRuleParserTest {
  @Test
  public void specificRulesHavePriorityOverGlobalRules() throws IOException {
    try (final InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream("spanRules.json")) {
      final Map<String,AdaptionRules> rules = AdaptionRuleParser.parseRules(in);
      assertTags(rules, "jedis", Arrays.asList(Collections.singletonMap("jedis", "select a"), Collections.singletonMap("*", "not matched")));
      assertTags(rules, "*", Arrays.asList(Collections.singletonMap("*", "select a"), Collections.singletonMap("*", "not matched")));
    }
  }

  private static void assertTags(Map<String,AdaptionRules> rules, String key, List<Map<String,String>> logs) {
    final AdaptionRules adaptionRules = rules.get(key);
    final MockTracer mockTracer = new MockTracer();
    try (final AdaptiveTracer tracer = new AdaptiveTracer(mockTracer, adaptionRules)) {
      final Span span = tracer.buildSpan("op").start();
      span.log(Collections.singletonMap("db.statement", "select a"));
      span.log(Collections.singletonMap("db.statement", "not matched"));
      span.finish();

      final List<MockSpan> spans = mockTracer.finishedSpans();
      assertEquals(1, spans.size());
      MockSpan mockSpan = spans.get(0);

      assertEquals(2, mockSpan.logEntries().size());
      for (int i = 0; i < logs.size(); ++i) {
        final Map<String,String> log = logs.get(i);
        assertEquals("log " + i, log, mockSpan.logEntries().get(i).fields());
      }
    }
  }

  @Test
  public void completeInvalidJson() {
    try {
      AdaptionRuleParser.parseRules(new ByteArrayInputStream("invalid".getBytes()));
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }
  }

  @Test
  public void oneRuleInvalidJson() {
    final String json = "{\"invalid\": 1, \"jedis\": []}";
    try {
      AdaptionRuleParser.parseRules(new ByteArrayInputStream(json.getBytes()));
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }
  }
}