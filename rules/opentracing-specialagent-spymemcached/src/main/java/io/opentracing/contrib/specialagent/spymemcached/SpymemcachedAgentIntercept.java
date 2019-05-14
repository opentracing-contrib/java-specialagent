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
package io.opentracing.contrib.specialagent.spymemcached;

import io.opentracing.Span;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collection;
import net.spy.memcached.ops.GetOperation;
import net.spy.memcached.ops.OperationCallback;

public class SpymemcachedAgentIntercept {
  private static final String DB_TYPE = "memcached";
  private static final String COMPONENT_NAME = "java-memcached";

  public static Object store(Object storeType, Object key, Object callback) {
    Span span = spanBuilder(storeType.toString())
        .withTag("key", key.toString()).start();

    return new TracingStoreOperationCallback((OperationCallback) callback, span);
  }

  public static Object get(Object key, Object callback) {
    SpanBuilder spanBuilder = spanBuilder("get");

    if (key instanceof Collection) {
      spanBuilder.withTag("keys", String.join(",", (Collection) key));
    } else {
      spanBuilder.withTag("key", key.toString()).start();
    }
    Span span = spanBuilder.start();

    return new TracingGetOperationCallback((GetOperation.Callback) callback, span);
  }

  private static SpanBuilder spanBuilder(String operation) {
    return GlobalTracer.get().buildSpan(operation)
        .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .withTag(Tags.DB_TYPE.getKey(), DB_TYPE);
  }

  public static Object delete(Object key, Object callback) {
    Span span = spanBuilder("delete")
        .withTag("key", key.toString()).start();
    return new TracingDeleteOperationCallback((OperationCallback) callback, span);
  }

  public static void exception(Throwable thrown, Object callback) {
    TracingOperationCallback tracingOperationCallback = (TracingOperationCallback) callback;
    tracingOperationCallback.onError(thrown);
  }

  public static Object getAndTouch(Object key, Object callback) {
    Span span = spanBuilder("getAndTouch")
        .withTag("key", key.toString()).start();

    return new TracingGetAndTouchOperationCallback((OperationCallback) callback, span);
  }

  public static Object gets(Object key, Object callback) {
    Span span = spanBuilder("gets")
        .withTag("key", key.toString()).start();

    return new TracingGetsOperationCallback((OperationCallback) callback, span);
  }

  public static Object tracingCallback(String operation, Object key, Object callback) {
    final SpanBuilder spanBuilder = spanBuilder(operation);
    if (key != null) {
      spanBuilder.withTag("key", key.toString());
    }
    Span span = spanBuilder.start();
    return new TracingOperationCallback((OperationCallback) callback, span);
  }

  public static Object cas(Object key, Object callback) {
    Span span = spanBuilder("cas")
        .withTag("key", key.toString()).start();

    return new TracingStoreOperationCallback((OperationCallback) callback, span);
  }
}