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

package io.opentracing.contrib.specialagent.zuul;

import com.netflix.zuul.ZuulFilter;
import io.opentracing.util.GlobalTracer;
import java.util.List;

public class ZuulAgentIntercept {

  public static Object exit(Object returned, Object arg) {
    List<ZuulFilter> filters = (List<ZuulFilter>) returned;
    if (arg.equals(TracePreZuulFilter.TYPE)) {
      filters.add(new TracePreZuulFilter(GlobalTracer.get()));

    } else if (arg.equals(TracePostZuulFilter.TYPE)) {
      filters.add(new TracePostZuulFilter());
    }
    return returned;
  }
}