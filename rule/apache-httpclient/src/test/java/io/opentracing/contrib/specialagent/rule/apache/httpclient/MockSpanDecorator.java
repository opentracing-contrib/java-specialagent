package io.opentracing.contrib.specialagent.rule.apache.httpclient;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import io.opentracing.Span;

public class MockSpanDecorator implements ApacheClientSpanDecorator {
  public static final String MOCK_TAG_KEY = "mock-tag-key";
  public static final String MOCK_TAG_VALUE = "mock-tag-value";

  @Override
  public void onRequest(HttpRequest request, HttpHost httpHost, Span span) {
    span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
  }

  @Override
  public void onResponse(HttpResponse response, Span span) {
    span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
  }

  @Override
  public void onError(Throwable thrown, Span span) {
    span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
  }
}
