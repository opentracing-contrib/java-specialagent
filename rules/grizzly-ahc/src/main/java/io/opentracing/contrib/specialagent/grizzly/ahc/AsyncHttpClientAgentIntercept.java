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

package io.opentracing.contrib.specialagent.grizzly.ahc;

import com.ning.http.client.AsyncHttpClientConfig;

import io.opentracing.contrib.grizzly.ahc.TracingRequestFilter;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;

public class AsyncHttpClientAgentIntercept {
  public static void exit(final @Advice.This Object thiz) {
    ((AsyncHttpClientConfig.Builder)thiz).addRequestFilter(new TracingRequestFilter(GlobalTracer.get()));
  }
}