/* Copyright 2019 The OpenTracing Authors
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;

public class InterceptUtil {
  public static final Logger logger = Logger.getLogger(InterceptUtil.class);
  public static final String SPAN_DECORATORS = "sa.instrumentation.plugin.servlet.spanDecorators";
  public static final String SKIP_PATTERN = "sa.instrumentation.plugin.servlet.skipPattern";
  public static final String DECORATOR_SEPARATOR = ",";

  private static List<ServletFilterSpanDecorator> spanDecorators = new ArrayList<>();
  private static Pattern skipPattern;

  static {
    initSpanDecorators();
    initSkipPattern();
  }

  public static List<ServletFilterSpanDecorator> getSpanDecorators() {
    return spanDecorators;
  }

  public static Pattern getSkipPattern() {
    return skipPattern;
  }

  public static boolean isTraced(final HttpServletRequest httpServletRequest) {
    if (skipPattern == null)
      return true;

    final int contextLength = httpServletRequest.getContextPath() == null ? 0 : httpServletRequest.getContextPath().length();
    final String url = httpServletRequest.getRequestURI().substring(contextLength);
    return !skipPattern.matcher(url).matches();
  }

  private static void initSpanDecorators() {
    final String spanDecoratorsArgs = System.getProperty(SPAN_DECORATORS);
    if (spanDecoratorsArgs != null) {
      final String[] parts = spanDecoratorsArgs.split(DECORATOR_SEPARATOR);
      for (final String part : parts) {
        final ServletFilterSpanDecorator decorator = newSpanDecoratorInstance(part);
        if (decorator != null)
          spanDecorators.add(decorator);
      }
    }

    if (spanDecorators.isEmpty())
      spanDecorators.add(ServletFilterSpanDecorator.STANDARD_TAGS);
  }

  private static void initSkipPattern() {
    final String skipPatternArgs = System.getProperty(SKIP_PATTERN);
    if (skipPatternArgs != null)
      skipPattern = Pattern.compile(skipPatternArgs);
  }

  private static ServletFilterSpanDecorator newSpanDecoratorInstance(final String className) {
    try {
      final Class<?> decoratorClass = Class.forName(className);
      if (ServletFilterSpanDecorator.class.isAssignableFrom(decoratorClass))
        return (ServletFilterSpanDecorator)decoratorClass.newInstance();

      logger.log(Level.WARNING, className + " is not a subclass of " + ServletFilterSpanDecorator.class.getName());
    }
    catch (final ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
    }

    return null;
  }
}