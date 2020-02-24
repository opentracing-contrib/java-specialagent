package io.opentracing.contrib.specialagent;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.HashMap;

public class SpanUtil {

  public static void onError(final Throwable t, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    if (t != null)
      span.log(errorLogs(t));
  }

  private static HashMap<String,Object> errorLogs(final Throwable t) {
    final HashMap<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", t);
    return errorLogs;
  }
}
