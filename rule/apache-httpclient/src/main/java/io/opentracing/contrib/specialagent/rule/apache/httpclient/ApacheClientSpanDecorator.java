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

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import java.net.URI;
import java.util.HashMap;

public interface ApacheClientSpanDecorator {
  void onRequest(HttpRequest request, HttpHost httpHost, Span span);
  void onResponse(HttpResponse response, Span span);
  void onError(Throwable thrown, Span span);

  class StandardTags implements ApacheClientSpanDecorator {
    static final String COMPONENT_NAME = "java-httpclient";

    @Override
    public void onRequest(HttpRequest request, HttpHost httpHost, Span span) {
      Tags.COMPONENT.set(span, COMPONENT_NAME);
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.HTTP_METHOD.set(span, request.getRequestLine().getMethod());
      Tags.HTTP_URL.set(span, request.getRequestLine().getUri());

      if (request instanceof HttpUriRequest) {
        final URI uri = ((HttpUriRequest)request).getURI();
        setPeerHostPort(span, uri.getHost(), uri.getPort());
      }
      else if (httpHost != null) {
        setPeerHostPort(span, httpHost.getHostName(), httpHost.getPort());
      }
    }

    @Override
    public void onResponse(final HttpResponse response, final Span span) {
      Tags.HTTP_STATUS.set(span, response.getStatusLine().getStatusCode());
    }

    @Override
    public void onError(final Throwable thrown, final Span span) {
      final HashMap<String,Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", Tags.ERROR.getKey());
      errorLogs.put("error.object", thrown);
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(errorLogs);
    }

    private static void setPeerHostPort(final Span span, final String host, final int port) {
      span.setTag(Tags.PEER_HOSTNAME, host);
      if (port != -1)
        span.setTag(Tags.PEER_PORT, port);
    }
  }
}
