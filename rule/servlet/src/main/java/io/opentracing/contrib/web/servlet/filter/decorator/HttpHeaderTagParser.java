package io.opentracing.contrib.web.servlet.filter.decorator;

import io.opentracing.contrib.specialagent.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HttpHeaderTagParser {

  private static final Logger logger = Logger.getLogger(HttpHeaderTagParser.class);

  public static final String HTTP_HEADER_TAGS = "sa.httpHeaderTags";
  final static String VALUES_SEPARATOR = ",";
  final static String ASSIGN_CHAR = "=";

  public static List<HttpHeaderServletFilterSpanDecorator.HeaderEntry> parse() {
    final String tags = System.getProperty(HTTP_HEADER_TAGS);
    if (tags == null || tags.isEmpty()) {
      //no http header tags configured
      return Collections.emptyList();
    }

    List<HttpHeaderServletFilterSpanDecorator.HeaderEntry> result = new ArrayList<>();

    for (String part : tags.split(VALUES_SEPARATOR)) {
      String [] headerTag = part.split(ASSIGN_CHAR, 2);
      String header = headerTag[0];
      String tag;
      if (headerTag.length == 1) {
        tag = HttpHeaderServletFilterSpanDecorator.DEFAULT_TAG_PREFIX + header;
      } else {
        tag = headerTag[1];
      }

      logger.info("http header tag: " + header + "=" + tag);

      result.add(new HttpHeaderServletFilterSpanDecorator.HeaderEntry(header, tag));
    }

    return result;
  }
}
