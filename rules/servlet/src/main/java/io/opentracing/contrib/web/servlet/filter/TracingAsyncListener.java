package io.opentracing.contrib.web.servlet.filter;

import java.io.IOException;
import java.util.List;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;

public class TracingAsyncListener implements AsyncListener {
  private final Span span;
  private final List<ServletFilterSpanDecorator> spanDecorators;

  public TracingAsyncListener(final Span span, final List<ServletFilterSpanDecorator> spanDecorators) {
    this.span = span;
    this.spanDecorators = spanDecorators;
  }

  @Override
  public void onComplete(AsyncEvent event) throws IOException {
      HttpServletRequest httpRequest = (HttpServletRequest) event.getSuppliedRequest();
      HttpServletResponse httpResponse = (HttpServletResponse) event.getSuppliedResponse();
      for (ServletFilterSpanDecorator spanDecorator: spanDecorators) {
              spanDecorator.onResponse(httpRequest,
              httpResponse,
              span);
      }
      span.finish();
  }

  @Override
  public void onTimeout(AsyncEvent event) throws IOException {
      HttpServletRequest httpRequest = (HttpServletRequest) event.getSuppliedRequest();
      HttpServletResponse httpResponse = (HttpServletResponse) event.getSuppliedResponse();
      for (ServletFilterSpanDecorator spanDecorator : spanDecorators) {
            spanDecorator.onTimeout(httpRequest,
                httpResponse,
                event.getAsyncContext().getTimeout(),
                span);
        }
  }

  @Override
  public void onError(AsyncEvent event) throws IOException {
      HttpServletRequest httpRequest = (HttpServletRequest) event.getSuppliedRequest();
      HttpServletResponse httpResponse = (HttpServletResponse) event.getSuppliedResponse();
      for (ServletFilterSpanDecorator spanDecorator: spanDecorators) {
          spanDecorator.onError(httpRequest,
              httpResponse,
              event.getThrowable(),
              span);
      }
  }

  @Override
  public void onStartAsync(AsyncEvent event) throws IOException {
  }
}
