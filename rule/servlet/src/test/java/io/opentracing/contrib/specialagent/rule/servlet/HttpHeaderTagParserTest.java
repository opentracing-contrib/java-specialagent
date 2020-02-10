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

package io.opentracing.contrib.specialagent.rule.servlet;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import io.opentracing.contrib.specialagent.rule.servlet.ext.HttpHeaderTagParser;
import io.opentracing.contrib.web.servlet.filter.decorator.ServletFilterHeaderSpanDecorator;

public class HttpHeaderTagParserTest {
  private static void assertEntry(final ServletFilterHeaderSpanDecorator.HeaderEntry entry, final String header, final String tag) {
    assertEquals("header", header, entry.getHeader());
    assertEquals("tag", tag, entry.getTag());
  }

  @Test
  public void testParse() {
    System.setProperty(HttpHeaderTagParser.HTTP_HEADER_TAGS, "a,b=c=d");

    try {
      final List<ServletFilterHeaderSpanDecorator.HeaderEntry> entries = HttpHeaderTagParser.parse();
      assertEquals(2, entries.size());
      assertEntry(entries.get(0), "a", "http.header.a");
      assertEntry(entries.get(1), "b", "c=d");
    }
    finally {
      System.clearProperty(HttpHeaderTagParser.HTTP_HEADER_TAGS);
    }
  }

  @Test
  public void testNoProperty() {
    assertEquals(0, HttpHeaderTagParser.parse().size());
  }
}