package io.opentracing.contrib.specialagent.test.servlet.jetty;

import java.io.PrintWriter;

import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.util.GlobalTracer;

public class HelloAsyncServlet extends HttpServlet {
  private static final long serialVersionUID = -4574837341343806228L;

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
    final AsyncContext asyncContext = request.startAsync(request, response);

    TestUtil.checkActiveSpan();

    new Thread() {
      @Override
      public void run() {
        try {
          TestUtil.checkActiveSpan();

          final ServletResponse response = asyncContext.getResponse();
          response.setContentType("text/plain");
          final PrintWriter out = response.getWriter();
          out.println("Async Servlet active span: " + GlobalTracer.get().activeSpan());
          out.flush();
          asyncContext.complete();
        }
        catch (final Exception e) {
          throw new RuntimeException(e);
        }
      }
    }.start();
  }
}