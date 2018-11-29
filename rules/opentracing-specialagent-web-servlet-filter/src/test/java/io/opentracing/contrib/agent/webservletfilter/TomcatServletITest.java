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
package io.opentracing.contrib.agent.webservletfilter;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author gbrown
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(debug=true, verbose=true, isolateClassLoader=false)
public class TomcatServletITest {
  private int serverPort = 9786;
  private Tomcat tomcatServer;

  @Before
  public void beforeTest() throws LifecycleException, ServletException {
    tomcatServer = new Tomcat();
    tomcatServer.setPort(serverPort);

    final File baseDir = new File("tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    final File applicationDir = new File(baseDir + "/webapps", "/ROOT");
    applicationDir.mkdirs();

    final Context appContext = tomcatServer.addWebapp("", applicationDir.getAbsolutePath());
    Tomcat.addServlet(appContext, "helloWorldServlet", new MockServlet());
    appContext.addServletMappingDecoded("/hello", "helloWorldServlet");

    tomcatServer.start();
    System.out.println("Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");
  }

  @Test
  public void testHelloRequest(final MockTracer tracer) throws IOException {
    final OkHttpClient client = new OkHttpClient();
    final Request request = new Request.Builder().url("http://localhost:" + serverPort + "/hello").build();
    final Response response = client.newCall(request).execute();

    assertEquals(HttpServletResponse.SC_ACCEPTED, response.code());
    assertEquals(1, tracer.finishedSpans().size());
  }

  @After
  public void afterTest() throws LifecycleException {
    tomcatServer.stop();
  }
}