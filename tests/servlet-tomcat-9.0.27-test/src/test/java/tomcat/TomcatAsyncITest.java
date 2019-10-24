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

package tomcat;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.Test;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.util.GlobalTracer;

public class TomcatAsyncITest {
  private static final Logger logger = Logger.getLogger(TomcatAsyncITest.class.getName());
  private static final int serverPort = 9787;

  public static void main(final String[] args) throws Exception {
    new TomcatAsyncITest().test();
  }

  @Test
  public void test() throws LifecycleException, IOException {
    final Tomcat tomcatServer = new Tomcat();
    tomcatServer.setPort(serverPort);

    final File baseDir = new File("target/tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    tomcatServer.getConnector();

    final File applicationDir = new File(new File(baseDir, "webapps"), "ROOT");
    applicationDir.mkdirs();

    final Context appContext = tomcatServer.addWebapp("", applicationDir.getAbsolutePath());

    Tomcat.addServlet(appContext, "helloWorldAsyncServlet", new HttpServlet() {
      private static final long serialVersionUID = 6184640156851545023L;

      @Override
      public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        if (GlobalTracer.get().activeSpan() == null)
          throw new AssertionError("ERROR: no active span");

        final AsyncContext asyncContext = request.startAsync(request, response);
        new Thread() {
          @Override
          public void run() {
            try {
              if (GlobalTracer.get().activeSpan() == null)
                throw new AssertionError("ERROR: no active span");

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
    }).setAsyncSupported(true);
    appContext.addServletMappingDecoded("/async", "helloWorldAsyncServlet");

    tomcatServer.start();
    logger.info("Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");

    final URL url = new URL("http://localhost:" + serverPort + "/async");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();

    if (HttpServletResponse.SC_OK != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    tomcatServer.stop();
    tomcatServer.destroy();
    TestUtil.checkSpan("java-web-servlet", 1);
  }
}