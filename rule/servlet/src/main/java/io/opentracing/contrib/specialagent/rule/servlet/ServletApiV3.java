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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.TracingAsyncListener;

public final class ServletApiV3 {
  public static boolean isApiV3;

  static {
    try {
      Class.forName("javax.servlet.AsyncListener");
      isApiV3 = true;
    }
    catch (final ClassNotFoundException e) {
      isApiV3 = false;
    }
  }

  public static int getStatus(final HttpServletResponse response) {
    return isApiV3 ? response.getStatus() : 200;
  }

  public static boolean isAsyncStarted(final HttpServletRequest request) {
    return isApiV3 && request.isAsyncStarted();
  }

  public static void addListenerToAsyncContext(final HttpServletRequest request, final Span span, final List<ServletFilterSpanDecorator> spanDecorators) {
    if (isApiV3)
      request.getAsyncContext().addListener(new TracingAsyncListener(span, spanDecorators));
  }

  private ServletApiV3() {
  }
}