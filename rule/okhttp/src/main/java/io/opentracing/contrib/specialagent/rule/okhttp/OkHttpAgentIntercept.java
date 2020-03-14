/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.okhttp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator;
import io.opentracing.contrib.okhttp3.TracingInterceptor;
import io.opentracing.util.GlobalTracer;
import okhttp3.Interceptor;

public class OkHttpAgentIntercept {
  @SuppressWarnings("unchecked")
  public static Object exit(final Object returned) {
    System.out.println("OkHttpAgentIntercept: " + OkHttpAgentIntercept.class.getClassLoader());
    final List<Interceptor> interceptors = (List<Interceptor>)returned;
    for (final Interceptor interceptor : interceptors)
      if (interceptor instanceof TracingInterceptor)
        return returned;

    final ArrayList<Interceptor> newInterceptors = new ArrayList<>(interceptors);
    final TracingInterceptor interceptor = new TracingInterceptor(GlobalTracer.get(), Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    System.err.println("TracingInterceptor: " + Interceptor.class.getClassLoader() + " " + OkHttpAgentIntercept.class.getClassLoader());
    newInterceptors.add(0, interceptor);
    return newInterceptors;
  }
}