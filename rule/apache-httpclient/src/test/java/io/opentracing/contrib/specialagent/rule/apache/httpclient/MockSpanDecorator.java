/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.apache.httpclient;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

import io.opentracing.Span;

public class MockSpanDecorator implements ApacheClientSpanDecorator {
  public static final String MOCK_TAG_KEY = "mock-tag-key";
  public static final String MOCK_TAG_VALUE = "mock-tag-value";

  @Override
  public void onRequest(final HttpRequest request, final HttpHost httpHost, final Span span) {
    span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
  }

  @Override
  public void onResponse(final HttpResponse response, final Span span) {
    span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
  }

  @Override
  public void onError(final Throwable thrown, final Span span) {
    span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
  }
}