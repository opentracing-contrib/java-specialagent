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

package io.opentracing.contrib.specialagent.webservletfilter;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.servlet.Filter;
import javax.servlet.ServletContext;

public abstract class ContextAgentIntercept {
  public static final String TRACING_FILTER_NAME = "tracingFilter";
  public static final String[] patterns = {"/*"};

  public static Method getFilterMethod(final ServletContext context) {
    try {
      final Method method = context.getClass().getMethod("addFilter", String.class, Filter.class);
      return Modifier.isAbstract(method.getModifiers()) ? null : method;
    }
    catch (final NoSuchMethodException e) {
      return null;
    }
  }
}