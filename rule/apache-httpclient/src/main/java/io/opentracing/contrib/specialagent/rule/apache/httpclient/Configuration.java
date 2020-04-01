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

package io.opentracing.contrib.specialagent.rule.apache.httpclient;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;

public class Configuration {
  public static final Logger logger = Logger.getLogger(Configuration.class);
  public static final String SPAN_DECORATORS = "sa.integration.apache:httpclient.spanDecorators";
  public static final String SPAN_DECORATORS_CLASSPATH = "sa.integration.apache:httpclient.spanDecorators.classpath";
  public static final String DECORATOR_SEPARATOR = ",";

  public static final List<ApacheClientSpanDecorator> spanDecorators = parseSpanDecorators(System.getProperty(SPAN_DECORATORS));
  private static ClassLoader decoratorClassLoader;

  static List<ApacheClientSpanDecorator> parseSpanDecorators(final String spanDecoratorsArgs) {
    final List<ApacheClientSpanDecorator> result = new ArrayList<>();
    if (spanDecoratorsArgs != null) {
      final String[] parts = spanDecoratorsArgs.split(DECORATOR_SEPARATOR);
      for (final String part : parts) {
        final ApacheClientSpanDecorator decorator = newSpanDecoratorInstance(part);
        if (decorator != null)
          result.add(decorator);
      }
    }

    if (result.isEmpty())
      result.add(new ApacheClientSpanDecorator.StandardTags());

    return result;
  }

  private static ApacheClientSpanDecorator newSpanDecoratorInstance(final String className) {
    try {
      final Class<?> decoratorClass = getDecoratorClassLoader().loadClass(className);
      if (ApacheClientSpanDecorator.class.isAssignableFrom(decoratorClass))
        return (ApacheClientSpanDecorator)decoratorClass.newInstance();

      logger.log(Level.WARNING, className + " is not a subclass of " + ApacheClientSpanDecorator.class.getName());
    }
    catch (final ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
    }

    return null;
  }

  private static ClassLoader getDecoratorClassLoader() {
    if (decoratorClassLoader != null)
      return decoratorClassLoader;

    final String spanDecoratorsClassPath = System.getProperty(SPAN_DECORATORS_CLASSPATH);
    if (spanDecoratorsClassPath == null || spanDecoratorsClassPath.isEmpty())
      return decoratorClassLoader = ApacheClientSpanDecorator.class.getClassLoader();

    final String[] parts = spanDecoratorsClassPath.split(File.pathSeparator);
    final URL[] urls = new URL[parts.length];
    for (int i = 0; i < parts.length; ++i) {
      final String part = parts[i];
      try {
        urls[i] = new URL("file", "", part.endsWith(".jar") || part.endsWith("/") ? part : part + "/");
      }
      catch (final MalformedURLException e) {
        logger.log(Level.WARNING, part + "is not a valid URL");
      }
    }

    return decoratorClassLoader = new URLClassLoader(urls, ApacheClientSpanDecorator.class.getClassLoader());
  }
}