package io.opentracing.contrib.web.servlet.filter.decorator;

import org.junit.Test;

import io.opentracing.contrib.specialagent.rule.servlet.ext.HttpHeaderTagParser;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HttpHeaderTagParserTest {

  @Test
  public void testParse() {
    System.setProperty(HttpHeaderTagParser.HTTP_HEADER_TAGS, "a,b=c=d");

    try {
      List<ServletFilterHeaderSpanDecorator.HeaderEntry> entries = HttpHeaderTagParser.parse();
      assertEquals(2, entries.size());
      assertEntry(entries.get(0), "a", "http.header.a");
      assertEntry(entries.get(1), "b", "c=d");
    } finally {
      System.clearProperty(HttpHeaderTagParser.HTTP_HEADER_TAGS);
    }
  }

  public void assertEntry(ServletFilterHeaderSpanDecorator.HeaderEntry entry, String header, String tag) {
    assertEquals("header", header, entry.getHeader());
    assertEquals("tag", tag, entry.getTag());
  }

  @Test
  public void testNoProperty() {
    assertEquals(0, HttpHeaderTagParser.parse().size());
  }
}
