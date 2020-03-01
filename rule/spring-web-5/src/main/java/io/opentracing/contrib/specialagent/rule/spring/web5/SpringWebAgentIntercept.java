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

package io.opentracing.contrib.specialagent.rule.spring.web5;

import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.rule.spring.web5.copied.TracingAsyncRestTemplateInterceptor;
import io.opentracing.contrib.specialagent.rule.spring.web5.copied.TracingRestTemplateInterceptor;
import io.opentracing.util.GlobalTracer;

@SuppressWarnings("deprecation")
public class SpringWebAgentIntercept {
  public static void enter(final Object thiz) {
    final RestTemplate restTemplate = (RestTemplate)thiz;
    for (final ClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors())
      if (interceptor instanceof TracingRestTemplateInterceptor)
        return;

    restTemplate.getInterceptors().add(new TracingRestTemplateInterceptor(GlobalTracer.get()));
  }

  public static void enterAsync(final Object thiz) {
    final AsyncRestTemplate restTemplate = (AsyncRestTemplate)thiz;
    for (final AsyncClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors())
      if (interceptor instanceof TracingAsyncRestTemplateInterceptor)
        return;

    restTemplate.getInterceptors().add(new TracingAsyncRestTemplateInterceptor(GlobalTracer.get()));
  }
}