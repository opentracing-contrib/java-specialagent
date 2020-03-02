package io.opentracing.contrib.specialagent.rewrite;

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
      final Map<String,RewriteRules> rules = RewriteRules.parseRules(in);
      assertTags(rules, "jedis", Arrays.asList(Collections.singletonMap("jedis", "select a"), Collections.singletonMap("*", "not matched")));
      assertTags(rules, "*", Arrays.asList(Collections.singletonMap("*", "select a"), Collections.singletonMap("*", "not matched")));
    }
  }

  private static void assertTags(final Map<String,RewriteRules> rules, final String key, final List<Map<String,String>> logs) {
    final RewriteRules rewriteRules = rules.get(key);
    final MockTracer mockTracer = new MockTracer();
    try (final RewritableTracer tracer = new RewritableTracer(mockTracer, rewriteRules)) {
      final Span span = tracer.buildSpan("op").start();
      span.log(Collections.singletonMap("db.statement", "select a"));
      span.log(Collections.singletonMap("db.statement", "not matched"));
      span.finish();

      final List<MockSpan> spans = mockTracer.finishedSpans();
      assertEquals(1, spans.size());
      final MockSpan mockSpan = spans.get(0);

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
      RewriteRules.parseRules(new ByteArrayInputStream("invalid".getBytes()));
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }
  }

  @Test
  public void oneRuleInvalidJson() {
    final String json = "{\"invalid\": 1, \"jedis\": []}";
    try {
      RewriteRules.parseRules(new ByteArrayInputStream(json.getBytes()));
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }
  }
}