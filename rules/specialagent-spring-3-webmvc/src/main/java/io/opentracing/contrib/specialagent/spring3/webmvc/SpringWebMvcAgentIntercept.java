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

package io.opentracing.contrib.specialagent.spring3.webmvc;

import org.springframework.web.servlet.HandlerInterceptor;

import io.opentracing.contrib.specialagent.spring3.webmvc.copied.TracingHandlerInterceptor;
import io.opentracing.util.GlobalTracer;

public class SpringWebMvcAgentIntercept {
  public static Object getInterceptors(final Object thiz) {
    try {
      Class.forName("org.springframework.web.method.HandlerMethod");
      // Spring 3.0 doesn't have it
      return thiz;
    }
    catch (final ClassNotFoundException ignore) {
    }

    final HandlerInterceptor[] interceptors = (HandlerInterceptor[])thiz;
    if (interceptors == null || interceptors.length == 0)
      return new HandlerInterceptor[] {new TracingHandlerInterceptor(GlobalTracer.get())};

    final HandlerInterceptor[] interceptorsCopy = new HandlerInterceptor[interceptors.length + 1];
    interceptorsCopy[interceptorsCopy.length - 1] = new TracingHandlerInterceptor(GlobalTracer.get());
    System.arraycopy(interceptors, 0, interceptorsCopy, 0, interceptors.length);

    return interceptorsCopy;
  }
}