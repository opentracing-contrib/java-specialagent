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

package io.opentracing.contrib.specialagent.rule.cxf;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptor;

import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;

public final class Configuration {
  public static final Logger logger = Logger.getLogger(Configuration.class);
  public static final String INTERCEPTORS_SERVER_IN = "sa.integration.cxf.interceptors.server.in";
  public static final String INTERCEPTORS_SERVER_OUT = "sa.integration.cxf.interceptors.server.out";
  public static final String INTERCEPTORS_CLIENT_IN = "sa.integration.cxf.interceptors.client.in";
  public static final String INTERCEPTORS_CLIENT_OUT = "sa.integration.cxf.interceptors.client.out";
  public static final String INTERCEPTORS_CLASSPATH = "sa.integration.cxf.interceptors.classpath";
  public static final String INTERCEPTORS_SEPARATOR = ",";

  private static ClassLoader interceptorClassLoader;

  public static List<PhaseInterceptor<Message>> getServerInInterceptors() {
    return parseInterceptors(INTERCEPTORS_SERVER_IN);
  }

  public static List<PhaseInterceptor<Message>> getServerOutInterceptors() {
    return parseInterceptors(INTERCEPTORS_SERVER_OUT);
  }

  public static List<PhaseInterceptor<Message>> getClientInInterceptors() {
    return parseInterceptors(INTERCEPTORS_CLIENT_IN);
  }

  public static List<PhaseInterceptor<Message>> getClientOutInterceptors() {
    return parseInterceptors(INTERCEPTORS_CLIENT_OUT);
  }

  private static List<PhaseInterceptor<Message>> parseInterceptors(final String interceptorsPropertyKey) {
    final String interceptorsProperty = System.getProperty(interceptorsPropertyKey);
    final List<PhaseInterceptor<Message>> result = new ArrayList<>();
    if (interceptorsProperty != null) {
      final String[] parts = interceptorsProperty.split(INTERCEPTORS_SEPARATOR);
      for (final String part : parts) {
        final PhaseInterceptor<Message> interceptor = newInterceptorInstance(part);
        if (interceptor != null)
          result.add(interceptor);
      }
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  private static PhaseInterceptor<Message> newInterceptorInstance(final String className) {
    try {
      final Class<?> interceptorClass = getInterceptorClassLoader().loadClass(className);
      if (PhaseInterceptor.class.isAssignableFrom(interceptorClass))
        return (PhaseInterceptor<Message>)interceptorClass.newInstance();

      logger.log(Level.WARNING, className + " is not a subclass of " + PhaseInterceptor.class.getName());
    }
    catch (final ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
    }

    return null;
  }

  private static ClassLoader getInterceptorClassLoader() {
    if (interceptorClassLoader != null)
      return interceptorClassLoader;

    final String interceptorClassPath = System.getProperty(INTERCEPTORS_CLASSPATH);
    if (interceptorClassPath == null || interceptorClassPath.isEmpty())
      return interceptorClassLoader = Configuration.class.getClassLoader();

    final String[] parts = interceptorClassPath.split(File.pathSeparator);
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

    return interceptorClassLoader = new URLClassLoader(urls, Configuration.class.getClassLoader());
  }

  private Configuration() {
  }
}