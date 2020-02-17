package io.opentracing.contrib.specialagent.tracer;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.Test;

public class SpanRuleParserTest {
  @Test
  public void parseRules() throws IOException {
    try (final InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream("spanRules.json")) {
      final Map<String,SpanRules> rules = SpanRuleParser.parseRules(in);
      assertEquals(1, rules.size());
      assertEquals("jedis", rules.keySet().iterator().next());
    }
  }

  @Test
  public void completeInvalidJson() {
    try {
      assertEquals(0, SpanRuleParser.parseRules(new ByteArrayInputStream("invalid".getBytes())).size());
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }
  }

  @Test
  public void oneRuleInvalidJson() {
    final String json = "{\"invalid\": 1, \"jedis\": []}";
    try {
      assertEquals(1, SpanRuleParser.parseRules(new ByteArrayInputStream(json.getBytes())).size());
      fail("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException e) {
    }
  }
}