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

package io.opentracing.contrib.specialagent.rule.concurrent;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class TracedRunnable implements Runnable {
  private final Runnable delegate;
  private final Span parent;
  private final boolean verbose;

  public TracedRunnable(final Runnable delegate, final Span parent, final boolean verbose) {
    this.delegate = delegate;
    this.parent = parent;
    this.verbose = verbose;
  }

  @Override
  public void run() {
    final Tracer tracer = GlobalTracer.get();
    if (verbose) {
      final Span span = tracer
        .buildSpan("runnable")
        .withTag(Tags.COMPONENT, "java-concurrent")
        .addReference(References.FOLLOWS_FROM, parent.context())
        .start();
      try (final Scope scope = tracer.activateSpan(span)) {
        delegate.run();
      }
      finally {
        span.finish();
      }
    }
    else {
      try (final Scope scope = tracer.activateSpan(parent)) {
        delegate.run();
      }
    }
  }
}