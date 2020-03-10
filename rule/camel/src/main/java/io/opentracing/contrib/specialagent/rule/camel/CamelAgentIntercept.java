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

package io.opentracing.contrib.specialagent.rule.camel;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.opentracing.OpenTracingTracer;

public class CamelAgentIntercept {
  public static final Map<DefaultCamelContext,Object> state = Collections.synchronizedMap(new WeakHashMap<DefaultCamelContext,Object>());

  public static void enter(final Object thiz) {
    final DefaultCamelContext context = (DefaultCamelContext)thiz;
    if (state.containsKey(context))
      return;

    final OpenTracingTracer tracer = new OpenTracingTracer();
    tracer.init(context);
    state.put(context, null);
  }
}