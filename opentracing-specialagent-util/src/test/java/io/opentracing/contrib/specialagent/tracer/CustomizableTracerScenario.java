package io.opentracing.contrib.specialagent.tracer;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum CustomizableTracerScenario {
  complex {
    @Override
    void play(Tracer tracer) {
      Span span = tracer.buildSpan("operation")
        .withTag("t1", "tv1")
        .withTag("t2", 1)
        .withTag("t3", true)
        .start();

      span.log(1, "event");
      span.log(2, logOrTagMap());
      span.finish();
    }
  },
  logEvent {
    @Override
    void play(Tracer tracer) {
      Span span = tracer.buildSpan("operation").start();
      span.log("event");
      span.finish();
    }
  },
  logEventTimestamp {
    @Override
    void play(Tracer tracer) {
      Span span = tracer.buildSpan("operation").start();
      span.log(1, "event");
      span.finish();
    }
  },
  logFields {
    @Override
    void play(Tracer tracer) {
      Span span = tracer.buildSpan("operation").start();
      span.log(Collections.singletonMap("key", "value"));
      span.finish();
    }
  },
  logFieldsTimestamp {
    @Override
    void play(Tracer tracer) {
      logFieldsSpan(tracer, Collections.singletonMap("key", "value"));
    }
  },
  logFieldsTypes {
    @Override
    void play(Tracer tracer) {
      logFieldsSpan(tracer, logOrTagMap());
    }
  },
  logFieldsNumber {
    @Override
    void play(Tracer tracer) {
      logFieldsSpan(tracer, Collections.singletonMap("key", 1));
      logFieldsSpan(tracer, Collections.singletonMap("key", 1L));
      logFieldsSpan(tracer, Collections.singletonMap("key", 1f));
      logFieldsSpan(tracer, Collections.singletonMap("key", 1d));
    }
  },
  operationName {
    @Override
    void play(Tracer tracer) {
      //will keep the operation name even if no output matches
      tracer.buildSpan("operation").start().finish();
    }
  },
  operationName2Spans {
    @Override
    void play(Tracer tracer) {
      tracer.buildSpan("newOperation").start().finish();

      //should behave the same when we set the operation name later
      Span late = tracer.buildSpan("oldOperation").start();
      late.setOperationName("newOperation");
      late.finish();
    }
  },
  operationNameLate {
    @Override
    void play(Tracer tracer) {
      Span late = tracer.buildSpan("operation").start();
      //and it's even possible to ignore setOperationName completely
      late.setOperationName("strange");
      late.finish();
    }
  },
  tag {
    @Override
    void play(Tracer tracer) {
      tracer.buildSpan("operation").withTag("key", "value").start().finish();
    }
  },
  tag2Spans {
    @Override
    void play(Tracer tracer) {
      tracer.buildSpan("operation").withTag("key", "value").start().finish();

      //should behave the same when we set the tag later
      Span late = tracer.buildSpan("operation").start();
      late.setTag("key", "value");
      late.finish();
    }
  },
  tagHttpUrl {
    @Override
    void play(Tracer tracer) {
      tracer.buildSpan("operation").withTag(Tags.HTTP_URL, "http://example.com").start().finish();

      Span span = tracer.buildSpan("operation").start();
      span.setTag(Tags.HTTP_URL, "http://example.com");
      span.finish();
    }
  },
  tagNumber{
    @Override
    void play(Tracer tracer) {
      tracer.buildSpan("operation").withTag("key", 1).start().finish();
      tracer.buildSpan("operation").withTag("key", 1L).start().finish();
      tracer.buildSpan("operation").withTag("key", 1f).start().finish();
      tracer.buildSpan("operation").withTag("key", 1d).start().finish();
    }
  },
  tagTypes {
    @Override
    void play(Tracer tracer) {
      tracer.buildSpan("operation")
        .withTag("k1", "v1")
        .withTag("k2", 1)
        .withTag("k3", false)
        .start().finish();

      //should behave the same when we set the tag later
      Span late = tracer.buildSpan("operation").start();
      late.setTag("k1", "v1");
      late.setTag("k2", 1);
      late.setTag("k3", false);
      late.finish();
    }
  };

  private static void logFieldsSpan(Tracer tracer, Map<String, ? extends Object> fields) {
    Span span = tracer.buildSpan("operation").start();
    span.log(1, fields);
    span.finish();
  }

  private static Map<String, Object> logOrTagMap() {
    LinkedHashMap<String, Object> log = new LinkedHashMap<>();
    log.put("k1", "v1");
    log.put("k2", 1);
    log.put("k3", false);
    return log;
  }

  abstract void play(Tracer tracer);
}
