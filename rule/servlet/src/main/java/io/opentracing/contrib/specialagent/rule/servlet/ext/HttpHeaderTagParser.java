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

package io.opentracing.contrib.specialagent.rule.servlet.ext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.web.servlet.filter.decorator.ServletFilterHeaderSpanDecorator;

public class HttpHeaderTagParser {
  private static final Logger logger = Logger.getLogger(HttpHeaderTagParser.class);

  public static final String HTTP_HEADER_TAGS = "sa.httpHeaderTags";
  final static String VALUES_SEPARATOR = ",";
  final static String ASSIGN_CHAR = "=";

  public static List<ServletFilterHeaderSpanDecorator.HeaderEntry> parse() {
    final String tags = System.getProperty(HTTP_HEADER_TAGS);
    if (tags == null || tags.isEmpty()) // no http header tags configured
      return Collections.emptyList();

    final List<ServletFilterHeaderSpanDecorator.HeaderEntry> result = new ArrayList<>();
    for (final String part : tags.split(VALUES_SEPARATOR)) {
      final String[] headerTag = part.split(ASSIGN_CHAR, 2);
      final String header = headerTag[0];
      final String tag;
      if (headerTag.length == 1)
        tag = ServletFilterHeaderSpanDecorator.DEFAULT_TAG_PREFIX + header;
      else
        tag = headerTag[1];

      logger.info("http header tag: " + header + "=" + tag);
      result.add(new ServletFilterHeaderSpanDecorator.HeaderEntry(header, tag));
    }

    return result;
  }
}