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

package io.opentracing.contrib.specialagent.rule.dubbo27;


import java.util.List;


import io.opentracing.contrib.specialagent.rule.GreeterServiceImpl;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Filter;
import org.junit.*;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;


public class DubboTest {

  protected static MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator
          .TEXT_MAP);

  private static MockClient client ;

  private static MockServer server ;


  @Before
  public void before() {
    mockTracer.reset();
  }

  @BeforeClass
  public static void setup() {
    GlobalTracer.registerIfAbsent(mockTracer);
    ExtensionLoader.getExtensionLoader(Filter.class).addExtension("traceFilter", DubboFilter.class);
    server = new MockServer();
    server.start();
    client = new MockClient(server.ip(), server.port());
  }

  @AfterClass
  public static void stop() {
    client.stop();
    server.stop();
  }

  @Test
  public void testNormalSpans() throws Exception {
    client.get().sayHello("jorge");
    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    Assert.assertEquals(2, mockSpans.size());
    Assert.assertEquals("GreeterService/sayHello", mockSpans.get(0).operationName());
    Assert.assertEquals("GreeterService/sayHello", mockSpans.get(1).operationName());
    Assert.assertEquals("server", mockSpans.get(0).tags().get(Tags.SPAN_KIND.getKey()));
    Assert.assertEquals("client", mockSpans.get(1).tags().get(Tags.SPAN_KIND.getKey()));
  }

  @Test
  public void testErrorSpans() throws Exception {
    GreeterServiceImpl.isThrowExecption = true;
    try {
      client.get().sayHello("jorge");
    } catch (Exception e) {
      Assert.assertEquals(GreeterServiceImpl.errorMesg, e.getMessage());
    }
    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    Assert.assertEquals(2, mockSpans.size());
    Assert.assertEquals("GreeterService/sayHello", mockSpans.get(0).operationName());
    Assert.assertEquals("GreeterService/sayHello", mockSpans.get(1).operationName());
    Assert.assertEquals(true, mockSpans.get(0).tags().get(Tags.ERROR.getKey()));
    Assert.assertEquals(true, mockSpans.get(1).tags().get(Tags.ERROR.getKey()));
  }
}