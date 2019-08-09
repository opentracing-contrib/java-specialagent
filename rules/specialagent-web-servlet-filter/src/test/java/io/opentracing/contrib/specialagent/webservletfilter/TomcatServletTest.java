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

package io.opentracing.contrib.specialagent.webservletfilter;

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
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.After;
import org.junit.Before;
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

  private final int serverPort = 9786;
  private Tomcat tomcatServer;

  @Before
  public void beforeTest() throws LifecycleException {
    MockFilter.count = 0;

    tomcatServer = new Tomcat();
    tomcatServer.setPort(serverPort);

    final File baseDir = new File("tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    final File applicationDir = new File(new File(baseDir, "webapps"), "ROOT");
    applicationDir.mkdirs();

    final Context context = tomcatServer.addWebapp("", applicationDir.getAbsolutePath());
    final FilterDef filterDef = new FilterDef();
    filterDef.setFilterName(MockFilter.class.getSimpleName());
    filterDef.setFilterClass(MockFilter.class.getName());
    context.addFilterDef(filterDef);

    final FilterMap filterMap = new FilterMap();
    filterMap.setFilterName(MockFilter.class.getSimpleName());
    filterMap.addURLPattern("/*");
    context.addFilterMap(filterMap);

    // Following triggers creation of NoPluggabilityServletContext object during initialization
    ((StandardContext)context).addApplicationLifecycleListener(new SCL());

    Tomcat.addServlet(context, "helloWorldServlet", new MockServlet());
    context.addServletMappingDecoded("/hello", "helloWorldServlet");

    tomcatServer.start();
    logger.info("Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");
  }

  @Test
  public void testHelloRequest(final MockTracer tracer) throws IOException {
    final OkHttpClient client = new OkHttpClient();
    final Request request = new Request.Builder().url("http://localhost:" + serverPort + "/hello").build();
    final Response response = client.newCall(request).execute();

    assertEquals(HttpServletResponse.SC_OK, response.code());
    assertEquals(1, MockFilter.count);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(spans.toString(), 1, spans.size());
  }

  public static class SCL implements ServletContextListener {
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
      // NOOP
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
      // NOOP
    }
  }

  @After
  public void afterTest() throws LifecycleException {
    tomcatServer.stop();
  }
}