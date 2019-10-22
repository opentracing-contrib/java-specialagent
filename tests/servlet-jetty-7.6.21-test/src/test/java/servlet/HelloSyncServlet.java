/* Copyright 2019 The OpenTracing Authors
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

package servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.util.GlobalTracer;

public class HelloSyncServlet extends HttpServlet {
  private static final long serialVersionUID = -930386857509367419L;

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
    TestUtil.checkActiveSpan();
    try (final PrintWriter out = response.getWriter()) {
      out.println("Sync Servlet active span: " + GlobalTracer.get().activeSpan());
    }
  }
}