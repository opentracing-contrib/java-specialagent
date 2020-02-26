package io.opentracing.contrib.web.servlet.filter;

import io.opentracing.contrib.specialagent.SpanUtil;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;
import io.opentracing.contrib.specialagent.rule.servlet.FilterAgentIntercept;
import io.opentracing.tag.Tags;

/**
 * Adds standard tags to span. {@link Tags#HTTP_URL}, {@link Tags#HTTP_STATUS}, {@link Tags#HTTP_METHOD} and
 * {@link Tags#COMPONENT}. If an exception during
 * {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)} is thrown tag
 * {@link Tags#ERROR} is added and {@link Tags#HTTP_STATUS} not because at this point it is not known.
 */
public class StandardTagsServletFilterSpanDecorator implements ServletFilterSpanDecorator {
    @Override
    public void onRequest(HttpServletRequest httpServletRequest, Span span) {
      Tags.COMPONENT.set(span, "java-web-servlet");

      Tags.HTTP_METHOD.set(span, httpServletRequest.getMethod());
      //without query params
      Tags.HTTP_URL.set(span, httpServletRequest.getRequestURL().toString());
    }

    @Override
    public void onResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                           Span span) {
      Tags.HTTP_STATUS.set(span, FilterAgentIntercept.getSatusCode(httpServletResponse));
    }

    @Override
    public void onError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                        Throwable exception, Span span) {
      SpanUtil.onError(exception, span);

      if (FilterAgentIntercept.getSatusCode(httpServletResponse) == HttpServletResponse.SC_OK) {
        // exception is thrown in filter chain, but status code is incorrect
        Tags.HTTP_STATUS.set(span, 500);
      }
    }

    @Override
    public void onTimeout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                          long timeout, Span span) {
      Map<String, Object> timeoutLogs = new HashMap<>(2);
      timeoutLogs.put("event", "timeout");
      timeoutLogs.put("timeout", timeout);
      span.log(timeoutLogs);
    }

  }