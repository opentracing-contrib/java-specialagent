package io.opentracing.contrib.specialagent.tracer;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SpanRuleParserTest {

  private SpanRuleParser parser = new SpanRuleParser();

  @Test
  public void parseRules() throws FileNotFoundException {
    Map<String, SpanRules> rules = parser.parseRules(new FileInputStream("src/test/resources/spanRules.json"));
    assertEquals(1, rules.size());
    assertEquals("jedis", rules.keySet().iterator().next());
  }

  @Test
  public void completeInvalidJson() {
    assertEquals(0, parser.parseRules(new ByteArrayInputStream("invalid".getBytes())).size());
  }

  @Test
  public void oneRuleInvalidJson() {
    String json = "{\"invalid\": 1, \"jedis\": []}";
    assertEquals(1, parser.parseRules(new ByteArrayInputStream(json.getBytes())).size());
  }
}
