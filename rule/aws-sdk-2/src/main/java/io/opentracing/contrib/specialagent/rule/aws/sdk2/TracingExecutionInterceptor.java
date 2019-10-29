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

package io.opentracing.contrib.specialagent.rule.aws.sdk2;

import java.util.HashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import software.amazon.awssdk.core.interceptor.Context.AfterExecution;
import software.amazon.awssdk.core.interceptor.Context.AfterMarshalling;
import software.amazon.awssdk.core.interceptor.Context.BeforeExecution;
import software.amazon.awssdk.core.interceptor.Context.FailedExecution;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;

public class TracingExecutionInterceptor implements ExecutionInterceptor {
  static final String COMPONENT_NAME = "java-aws-sdk";
  private static final ExecutionAttribute<Span> SPAN_ATTRIBUTE = new ExecutionAttribute<>("ot-span");

  @Override
  public void beforeExecution(BeforeExecution context, ExecutionAttributes executionAttributes) {
    final Span span = GlobalTracer.get().buildSpan(context.request().getClass().getSimpleName())
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.PEER_SERVICE, executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME))
      .withTag(Tags.COMPONENT, COMPONENT_NAME).start();

    executionAttributes.putAttribute(SPAN_ATTRIBUTE, span);
  }

  @Override
  public void afterMarshalling(final AfterMarshalling context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    final SdkHttpRequest httpRequest = context.httpRequest();

    span.setTag(Tags.HTTP_METHOD, httpRequest.method().name());
    span.setTag(Tags.HTTP_URL, httpRequest.getUri().toString());
    span.setTag(Tags.PEER_HOSTNAME, httpRequest.host());
    if (httpRequest.port() > 0)
      span.setTag(Tags.PEER_PORT, httpRequest.port());
  }

  @Override
  public void afterExecution(final AfterExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span == null)
      return;

    executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
    span.setTag(Tags.HTTP_STATUS, context.httpResponse().statusCode());
    span.finish();
  }

  @Override
  public void onExecutionFailure(final FailedExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span == null)
      return;

    executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
    Tags.ERROR.set(span, Boolean.TRUE);
    span.log(errorLogs(context.exception()));
    span.finish();
  }

  private static Map<String,Object> errorLogs(final Throwable ex) {
    Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", ex);
    return errorLogs;
  }
}