package io.opentracing.contrib.specialagent.spring.web.copied;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

/**
 * Decorate span by adding tags/logs or operation name change.
 *
 * <p>Do not finish span or throw any exceptions!
 *
 * @author Pavol Loffay
 */
public interface RestTemplateSpanDecorator {

  /**
   * Decorate span before before request is executed, e.g. before
   * {@link org.springframework.http.client.ClientHttpRequestInterceptor#intercept(HttpRequest, byte[], ClientHttpRequestExecution)}
   * is called.
   *
   * @param request request
   * @param span client span
   */
  void onRequest(HttpRequest request, Span span);

  /**
   * Decorate span after request is done, e.g. after
   * {@link org.springframework.http.client.ClientHttpRequestInterceptor#intercept(HttpRequest, byte[], ClientHttpRequestExecution)}
   * is called
   *
   * @param request request
   * @param response response
   * @param span span
   */
  void onResponse(HttpRequest request, ClientHttpResponse response, Span span);

  /**
   * Decorate span when exception is thrown during request processing, e.g. during
   * {@link org.springframework.http.client.ClientHttpRequestInterceptor#intercept(HttpRequest, byte[], ClientHttpRequestExecution)}
   * is processing.
   *
   * @param request request
   * @param ex exception
   * @param span span
   */
  void onError(HttpRequest request,  Throwable ex, Span span);

  /**
   * This decorator adds set of standard tags to the span.
   */
  class StandardTags implements RestTemplateSpanDecorator {
    private static final Log log = LogFactory.getLog(StandardTags.class);

    public static final String COMPONENT_NAME = "java-spring-rest-template";

    @Override
    public void onRequest(HttpRequest request, Span span) {
      Tags.COMPONENT.set(span, COMPONENT_NAME);
      // this can be sometimes only path e.g. "/foo"
      Tags.HTTP_URL.set(span, request.getURI().toString());
      Tags.HTTP_METHOD.set(span, request.getMethod().toString());

      if (request.getURI().getPort() != -1) {
        Tags.PEER_PORT.set(span, request.getURI().getPort());
      }
    }

    @Override
    public void onResponse(HttpRequest httpRequest, ClientHttpResponse response, Span span) {
      try {
        Tags.HTTP_STATUS.set(span, response.getRawStatusCode());
      } catch (IOException e) {
        log.error("Could not get HTTP status code");
      }
    }

    @Override
    public void onError(HttpRequest httpRequest, Throwable ex, Span span) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(errorLogs(ex));
    }

    public static Map<String, Object> errorLogs(Throwable ex) {
      Map<String, Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", Tags.ERROR.getKey());
      errorLogs.put("error.object", ex);
      return errorLogs;
    }
  }
}
