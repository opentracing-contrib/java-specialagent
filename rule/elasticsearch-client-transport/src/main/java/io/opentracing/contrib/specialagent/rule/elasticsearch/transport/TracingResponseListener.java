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

package io.opentracing.contrib.specialagent.rule.elasticsearch.transport;

import java.net.InetSocketAddress;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionResponse;

import io.opentracing.Span;
import io.opentracing.contrib.elasticsearch.common.SpanDecorator;
import io.opentracing.tag.Tags;

class TracingResponseListener<T extends ActionResponse> implements ActionListener<T> {
  private final ActionListener<T> listener;
  private final Span span;

  TracingResponseListener(final ActionListener<T> listener, final Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onResponse(final T t) {
    if (t.remoteAddress() != null) {
      final InetSocketAddress address = t.remoteAddress().address();
      if (address != null) {
        Tags.PEER_HOSTNAME.set(span, address.getHostName());
        Tags.PEER_PORT.set(span, address.getPort());
      }
    }

    try {
      listener.onResponse(t);
    }
    finally {
      span.finish();
    }
  }

  @Override
  public void onFailure(final Exception e) {
    SpanDecorator.onError(e, span);

    try {
      listener.onFailure(e);
    }
    finally {
      span.finish();
    }
  }
}