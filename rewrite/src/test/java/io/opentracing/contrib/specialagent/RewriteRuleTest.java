/* Copyright 2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

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

public class RewriteRuleTest {
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
      for (int i = 0; i < logs.size(); ++i)
        assertEquals("log " + i, logs.get(i), mockSpan.logEntries().get(i).fields());
    }
  }

  @Test
  public void specificRulesHavePriorityOverGlobalRules() throws IOException {
    try (final InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream("spanRules.json")) {
      final Map<String,RewriteRules> rules = RewriteRules.parseRules(in);
      assertTags(rules, "jedis", Arrays.asList(Collections.singletonMap("jedis", "select a"), Collections.singletonMap("*", "not matched")));
      assertTags(rules, "*", Arrays.asList(Collections.singletonMap("*", "select a"), Collections.singletonMap("*", "not matched")));
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