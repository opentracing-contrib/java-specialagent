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

package io.opentracing.contrib.specialagent.rule.spymemcached;

import java.util.Collection;
import java.util.Iterator;

import io.opentracing.Span;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import net.spy.memcached.ops.GetOperation;
import net.spy.memcached.ops.OperationCallback;

public class SpymemcachedAgentIntercept {
  private static final String DB_TYPE = "memcached";
  private static final String COMPONENT_NAME = "java-memcached";

  public static Object store(final Object storeType, final Object key, final Object callback) {
    final Span span = spanBuilder(storeType.toString()).withTag("key", key.toString()).start();
    return new TracingStoreOperationCallback((OperationCallback)callback, span);
  }

  @SuppressWarnings("unchecked")
  public static Object get(final Object key, final Object callback) {
    final SpanBuilder spanBuilder = spanBuilder("get");
    if (key instanceof Collection) {
      final Iterator<? extends CharSequence> iterator = ((Collection<? extends CharSequence>)key).iterator();
      final StringBuilder builder = new StringBuilder();
      for (int i = 0; iterator.hasNext(); ++i) {
        if (i > 0)
          builder.append(',');

        builder.append(iterator.next());
      }

      spanBuilder.withTag("keys", builder.toString());
    }
    else {
      spanBuilder.withTag("key", key.toString()).start();
    }

    final Span span = spanBuilder.start();
    return new TracingGetOperationCallback((GetOperation.Callback)callback, span);
  }

  private static SpanBuilder spanBuilder(final String operation) {
    return GlobalTracer.get()
      .buildSpan(operation)
      .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.DB_TYPE.getKey(), DB_TYPE);
  }

  public static Object delete(final Object key, final Object callback) {
    final Span span = spanBuilder("delete").withTag("key", key.toString()).start();
    return new TracingDeleteOperationCallback((OperationCallback)callback, span);
  }

  public static void exception(final Throwable thrown, final Object callback) {
    final TracingOperationCallback tracingOperationCallback = (TracingOperationCallback)callback;
    tracingOperationCallback.onError(thrown);
  }

  public static Object getAndTouch(final Object key, final Object callback) {
    final Span span = spanBuilder("getAndTouch").withTag("key", key.toString()).start();
    return new TracingGetAndTouchOperationCallback((OperationCallback)callback, span);
  }

  public static Object gets(final Object key, final Object callback) {
    final Span span = spanBuilder("gets").withTag("key", key.toString()).start();
    return new TracingGetsOperationCallback((OperationCallback)callback, span);
  }

  public static Object tracingCallback(final String operation, final Object key, final Object callback) {
    final SpanBuilder spanBuilder = spanBuilder(operation);
    if (key != null)
      spanBuilder.withTag("key", key.toString());

    final Span span = spanBuilder.start();
    return new TracingOperationCallback((OperationCallback)callback, span);
  }

  public static Object cas(final Object key, final Object callback) {
    final Span span = spanBuilder("cas").withTag("key", key.toString()).start();
    return new TracingStoreOperationCallback((OperationCallback)callback, span);
  }
}