package io.opentracing.contrib.specialagent.webservletfilter;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author gbrown
 * @author Seva Safris
 */
public class MockServlet extends HttpServlet {
  private static final long serialVersionUID = 976450353590523027L;

  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
    response.setStatus(HttpServletResponse.SC_ACCEPTED);
  }
}