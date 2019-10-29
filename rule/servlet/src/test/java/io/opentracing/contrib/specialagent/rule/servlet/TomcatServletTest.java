/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.servlet;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author gbrown
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader=false, disable="okhttp")
public class TomcatServletTest {
  private static final Logger logger = Logger.getLogger(TomcatServletTest.class);
  private static final int serverPort = 9786;
  private static Tomcat tomcatServer;

  @BeforeClass
  public static void beforeClass() throws LifecycleException {
    tomcatServer = new Tomcat();
    tomcatServer.setPort(serverPort);

    final File baseDir = new File("tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    final File applicationDir = new File(new File(baseDir, "webapps"), "ROOT");
    applicationDir.mkdirs();

    final Context context = tomcatServer.addContext("", applicationDir.getAbsolutePath());
    final FilterDef filterDef = new FilterDef();
    filterDef.setFilterName(MockFilter.class.getSimpleName());
    filterDef.setFilterClass(MockFilter.class.getName());
    context.addFilterDef(filterDef);

    final FilterMap filterMap = new FilterMap();
    filterMap.setFilterName(MockFilter.class.getSimpleName());
    filterMap.addURLPattern("/*");
    context.addFilterMap(filterMap);

    // Following triggers creation of NoPluggabilityServletContext object during initialization
    ((StandardContext)context).addApplicationLifecycleListener(new ServletContextListener() {
      @Override
      public void contextInitialized(final ServletContextEvent event) {
      }

      @Override
      public void contextDestroyed(final ServletContextEvent event) {
      }
    });

    Tomcat.addServlet(context, "helloWorldServlet", new MockServlet());
    context.addServletMapping("/hello", "helloWorldServlet");

    tomcatServer.start();
    logger.info("Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");
  }

  @Test
  public void testHelloRequest(final MockTracer tracer) throws IOException {
    MockFilter.count = 0;
    MockServlet.count = 0;

    final OkHttpClient client = new OkHttpClient();
    final Request request = new Request.Builder().url("http://localhost:" + serverPort + "/hello").build();
    final Response response = client.newCall(request).execute();

    assertEquals("MockServlet response", HttpServletResponse.SC_ACCEPTED, response.code());
    assertEquals("MockServlet count", 1, MockServlet.count);
    assertEquals("MockFilter count", 1, MockFilter.count);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals("MockTracer spans: " + spans, 1, spans.size());
  }

  @AfterClass
  public static void afterClass() throws LifecycleException {
    tomcatServer.stop();
  }
}