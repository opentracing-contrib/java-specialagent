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

package io.opentracing.contrib.specialagent.spring.webflux;


import io.opentracing.contrib.specialagent.spring.webflux.copied.TracingExchangeFilterFunction;
import io.opentracing.contrib.specialagent.spring.webflux.copied.TracingWebFilter;
import io.opentracing.contrib.specialagent.spring.webflux.copied.WebClientSpanDecorator;
import io.opentracing.contrib.specialagent.spring.webflux.copied.WebFluxSpanDecorator;
import io.opentracing.util.GlobalTracer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import org.springframework.web.server.WebFilter;

public class SpringWebFluxAgentIntercept {

  @SuppressWarnings("unchecked")
  public static Object filters(Object arg) {
    List<WebFilter> filters = (List<WebFilter>) arg;
    List<WebFilter> newFilters = new ArrayList<>(filters);
    newFilters.add(new TracingWebFilter(
        GlobalTracer.get(),
        Integer.MIN_VALUE,
        Pattern.compile(""),
        Collections.emptyList(),
        Arrays.asList(new WebFluxSpanDecorator.StandardTags(),
            new WebFluxSpanDecorator.WebFluxTags())));

    return Collections.unmodifiableList(newFilters);
  }

  public static void client(Object thiz) {
    WebClient.Builder builder = (Builder) thiz;
    builder.filter(new TracingExchangeFilterFunction(GlobalTracer.get(),
        Collections.singletonList(new WebClientSpanDecorator.StandardTags())));

  }
}