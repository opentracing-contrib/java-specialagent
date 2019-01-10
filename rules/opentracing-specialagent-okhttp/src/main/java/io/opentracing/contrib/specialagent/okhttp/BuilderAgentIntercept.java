package io.opentracing.contrib.specialagent.okhttp;

import java.util.Collections;

import io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator;
import io.opentracing.contrib.okhttp3.TracingInterceptor;
import io.opentracing.util.GlobalTracer;

public class BuilderAgentIntercept {
  public static void enter(final Object thiz) {
    final okhttp3.OkHttpClient.Builder builder = (okhttp3.OkHttpClient.Builder)thiz;
    final TracingInterceptor interceptor = new TracingInterceptor(GlobalTracer.get(), Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    builder.addInterceptor(interceptor);
    builder.addNetworkInterceptor(interceptor);
  }
}