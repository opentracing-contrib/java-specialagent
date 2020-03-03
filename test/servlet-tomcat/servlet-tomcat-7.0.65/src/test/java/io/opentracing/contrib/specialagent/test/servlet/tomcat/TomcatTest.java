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

package io.opentracing.contrib.specialagent.test.servlet.tomcat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.servlet.Servlet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public abstract class TomcatTest {
  private static final Logger logger = Logger.getLogger(TomcatAsyncITest.class.getName());
  private static final int serverPort = 9787;

  private static void test(final Tomcat tomcatServer) throws IOException, LifecycleException {
    tomcatServer.start();
    logger.info("Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");

    final URL url = new URL("http://localhost:" + serverPort + "/test");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();

    if (HttpServletResponse.SC_OK != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    tomcatServer.stop();
    tomcatServer.destroy();
    TestUtil.checkSpan(true, new ComponentSpanCount("java-web-servlet", 1), new ComponentSpanCount("http-url-connection", 1));
  }

  protected static void run(final Servlet servlet, final boolean async) throws IllegalAccessException, InvocationTargetException, IOException, LifecycleException, ServletException {
    final Tomcat tomcatServer = new Tomcat();
    tomcatServer.setPort(serverPort);

    final File baseDir = new File("target/tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    // Need to call this to activate the connector!
    tomcatServer.getConnector();

    final File applicationDir = new File(new File(baseDir, "webapps"), "ROOT");
    applicationDir.mkdirs();

    final StandardContext appContext = (StandardContext)tomcatServer.addWebapp("", applicationDir.getAbsolutePath());
    // Following triggers creation of NoPluggabilityServletContext
    // object during initialization.
    appContext.addApplicationLifecycleListener(new ServletContextListener() {
      @Override
      public void contextInitialized(final ServletContextEvent e) {
      }

      @Override
      public void contextDestroyed(final ServletContextEvent e) {
      }
    });

    Tomcat.addServlet(appContext, "helloWorldServlet", servlet).setAsyncSupported(async);
    for (final Method method : appContext.getClass().getMethods()) {
      if (method.getName().startsWith("addServletMapping") && Arrays.equals(method.getParameterTypes(), new Class[] {String.class, String.class})) {
        method.invoke(appContext, "/test", "helloWorldServlet");
        test(tomcatServer);
        return;
      }
    }

    throw new IllegalStateException("Was not able to find method: Context#addServletMapping*");
  }
}