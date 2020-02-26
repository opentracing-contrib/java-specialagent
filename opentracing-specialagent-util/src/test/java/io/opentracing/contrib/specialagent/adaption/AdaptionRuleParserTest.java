package io.opentracing.contrib.specialagent.adaption;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.Test;

public class AdaptionRuleParserTest {
  @Test
  public void parseRules() throws IOException {
    try (final InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream("spanRules.json")) {
      final Map<String,AdaptionRules> rules = AdaptionRuleParser.parseRules(in);
      assertEquals(1, rules.size());
      assertEquals("jedis", rules.keySet().iterator().next());
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