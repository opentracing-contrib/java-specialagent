package io.opentracing.contrib.specialagent.spring.web;

import io.opentracing.contrib.specialagent.spring.web.copied.TracingAsyncRestTemplateInterceptor;
import io.opentracing.contrib.specialagent.spring.web.copied.TracingRestTemplateInterceptor;
import io.opentracing.util.GlobalTracer;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

public class SpringWebAgentIntercept {
  public static void enter(Object thiz) {
    RestTemplate restTemplate = (RestTemplate) thiz;
    for (ClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors()) {
      if (interceptor instanceof TracingRestTemplateInterceptor) {
        return;
      }
    }
    restTemplate.getInterceptors().add(new TracingRestTemplateInterceptor(
        GlobalTracer.get()));
  }

  public static void enterAsync(Object thiz) {
    AsyncRestTemplate restTemplate = (AsyncRestTemplate) thiz;
    for (AsyncClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors()) {
      if (interceptor instanceof TracingAsyncRestTemplateInterceptor) {
        return;
      }
    }
    restTemplate.getInterceptors().add(new TracingAsyncRestTemplateInterceptor(
        GlobalTracer.get()));
  }
}
