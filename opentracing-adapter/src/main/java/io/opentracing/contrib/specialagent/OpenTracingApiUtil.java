package io.opentracing.contrib.specialagent;

import java.util.HashMap;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

public final class OpenTracingApiUtil {
  /**
   * Sets {@link Tags#ERROR} on the specified {@link Span span}, and logs the
   * provided {@link Throwable} if not null.
   *
   * @param span The {@link Span} to which {@link Tags#ERROR} is to be set.
   * @param t The {@link Throwable} is to be logged in the specified {@link Span span}, which can be null.
   * @throws NullPointerException If {@code span} is null.
   */
  public static void setErrorTag(final Span span, final Throwable t) {
    span.setTag(Tags.ERROR, Boolean.TRUE);
    if (t != null) {
      final HashMap<String,Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", Tags.ERROR.getKey());
      errorLogs.put("error.object", t);
      span.log(errorLogs);
    }
  }

  private OpenTracingApiUtil() {
  }
}