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

package io.opentracing.contrib.specialagent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

public enum RewritableTracerScenario {
  COMPLEX("complex") {
    @Override
    void play(final Tracer tracer) {
      final Span span = tracer.buildSpan("operation").withTag("t1", "tv1").withTag("t2", 1).withTag("t3", true).start();
      span.log(1, "event");
      span.log(2, logOrTagMap());
      span.finish();
    }
  },
  LOG_EVENT("logEvent") {
    @Override
    void play(final Tracer tracer) {
      final Span span = tracer.buildSpan("operation").start();
      span.log("event");
      span.finish();
    }
  },
  LOG_EVENT_TIMESTAMP("logEventTimestamp") {
    @Override
    void play(final Tracer tracer) {
      final Span span = tracer.buildSpan("operation").start();
      span.log(1, "event");
      span.finish();
    }
  },
  LOG_FIELDS("logFields") {
    @Override
    void play(final Tracer tracer) {
      final Span span = tracer.buildSpan("operation").start();
      span.log(Collections.singletonMap("key", "value"));
      span.finish();
    }
  },
  LOG_FIELDS_BOOLEAN("logFieldsBoolean") {
    @Override
    void play(final Tracer tracer) {
      logFieldsSpan(tracer, Collections.singletonMap("key", true));
    }
  },
  LOG_FIELDS_TIMESTAMP("logFieldsTimestamp") {
    @Override
    void play(final Tracer tracer) {
      logFieldsSpan(tracer, Collections.singletonMap("key", "value"));
    }
  },
  LOG_FIELDS_TYPES("logFieldsTypes") {
    @Override
    void play(final Tracer tracer) {
      logFieldsSpan(tracer, logOrTagMap());
    }
  },
  LOG_FIELDS_NUMBER("logFieldsNumber") {
    @Override
    void play(final Tracer tracer) {
      logFieldsSpan(tracer, Collections.singletonMap("key", 1));
      logFieldsSpan(tracer, Collections.singletonMap("key", 1L));
      logFieldsSpan(tracer, Collections.singletonMap("key", 1f));
      logFieldsSpan(tracer, Collections.singletonMap("key", 1d));
    }
  },
  OPERATION_NAME("operationName") {
    @Override
    void play(final Tracer tracer) {
      // will keep the operation name even if no output matches
      tracer.buildSpan("operation").start().finish();
    }
  },
  OPERATION_NAME_2_SPANS("operationName2Spans") {
    @Override
    void play(final Tracer tracer) {
      tracer.buildSpan("newOperation").start().finish();

      // should behave the same when we set the operation name later
      final Span late = tracer.buildSpan("oldOperation").start();
      late.setOperationName("newOperation");
      late.finish();
    }
  },
  OPERATION_NAME_LATE("operationNameLate") {
    @Override
    void play(final Tracer tracer) {
      final Span late = tracer.buildSpan("operation").start();
      // and it's even possible to ignore setOperationName completely
      late.setOperationName("strange");
      late.setOperationName("strange2");
      late.finish();
    }
  },
  TAG("tag") {
    @Override
    void play(final Tracer tracer) {
      tracer.buildSpan("operation").withTag("key", "value").start().finish();
    }
  },
  TAG_2_SPANS("tag2Spans") {
    @Override
    void play(final Tracer tracer) {
      tracer.buildSpan("operation").withTag("key", "value").start().finish();

      // should behave the same when we set the tag later
      final Span late = tracer.buildSpan("operation").start();
      late.setTag("key", "value");
      late.finish();
    }
  },
  TAG_HTTP_URL("tagHttpUrl") {
    @Override
    void play(final Tracer tracer) {
      tracer.buildSpan("operation").withTag(Tags.HTTP_URL, "http://example.com").start().finish();

      final Span span = tracer.buildSpan("operation").start();
      span.setTag(Tags.HTTP_URL, "http://example.com");
      span.finish();
    }
  },
  TAG_NUMBER("tagNumber") {
    @Override
    void play(final Tracer tracer) {
      tracer.buildSpan("operation").withTag("key", 1).start().finish();
      tracer.buildSpan("operation").withTag("key", 1L).start().finish();
      tracer.buildSpan("operation").withTag("key", 1f).start().finish();
      tracer.buildSpan("operation").withTag("key", 1d).start().finish();
    }
  },
  TAG_TYPES("tagTypes") {
    @Override
    void play(final Tracer tracer) {
      tracer.buildSpan("operation").withTag("k0", "v1").withTag("k1", "v1").withTag("k2", 1).withTag("k3", false).start().finish();

      // should behave the same when we set the tag later
      final Span late = tracer.buildSpan("operation").start();
      late.setTag("k0", "v1");
      late.setTag("k1", "v1");
      late.setTag("k2", 1);
      late.setTag("k3", false);
      late.finish();
    }
  };

  private final String tagName;

  private RewritableTracerScenario(final String tagName) {
    this.tagName = tagName;
  }

  private static void logFieldsSpan(final Tracer tracer, final Map<String,? extends Object> fields) {
    final Span span = tracer.buildSpan("operation").start();
    span.log(1, fields);
    span.finish();
  }

  private static Map<String,Object> logOrTagMap() {
    final LinkedHashMap<String,Object> log = new LinkedHashMap<>();
    log.put("k1", "v1");
    log.put("k2", 1);
    log.put("k3", false);
    return log;
  }

  abstract void play(Tracer tracer);

  public static RewritableTracerScenario fromString(final String str) {
    for (final RewritableTracerScenario value : values())
      if (value.tagName.equals(str))
        return value;

    return null;
  }
}