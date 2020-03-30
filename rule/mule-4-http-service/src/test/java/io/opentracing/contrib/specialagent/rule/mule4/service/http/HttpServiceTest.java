/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.mule4.service.http;

import static org.junit.Assert.*;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
public class HttpServiceTest extends AbstractMuleTestCase {
  private static final String HOST = "localhost";
  private static final int PORT = 9875;
  private HttpServiceImplementation service;
  private SimpleUnitTestSupportSchedulerService schedulerService;
  private HttpServer server;

  @Before
  public void before(final MockTracer tracer) throws Exception {
    tracer.reset();

    schedulerService = new SimpleUnitTestSupportSchedulerService();
    service = (HttpServiceImplementation)ClassUtils.instantiateClass(HttpServiceImplementation.class.getName(), new Object[] {schedulerService}, this.getClass().getClassLoader());
    service.start();

    server = service.getServerFactory().create(new HttpServerConfiguration.Builder().setHost(HOST).setPort(PORT).setName("test-server").build());
    server.start();
  }

  @After
  public void after() throws Exception {
    if (server != null)
      server.stop();

    if (service != null)
      service.stop();

    if (schedulerService != null)
      schedulerService.stop();
  }

  @Test
  // @TestConfig(verbose = true)
  public void httpServiceTest(final MockTracer tracer) throws Exception {
    final Response response = Request.Get("http://" + HOST + ":" + PORT + "/").execute();
    assertEquals(response.returnResponse().getStatusLine().getStatusCode(), 503);
    assertEquals(1, tracer.finishedSpans().size());
  }
}