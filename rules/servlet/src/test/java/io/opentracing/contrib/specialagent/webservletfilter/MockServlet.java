/* Copyright 2018 The OpenTracing Authors
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
  static volatile int count;

  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
    ++count;
    response.setStatus(HttpServletResponse.SC_ACCEPTED);
  }
}