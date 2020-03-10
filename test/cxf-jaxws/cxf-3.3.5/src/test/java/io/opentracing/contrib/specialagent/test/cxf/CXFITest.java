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

package io.opentracing.contrib.specialagent.test.cxf;

import java.util.List;
import javax.jws.WebService;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

public class CXFITest {
  private static final String BASE_URI = "http://127.0.0.1:48080";

  public static void main(final String[] args) {
    final String msg = "hello";

    final JaxWsServerFactoryBean serverFactory = new JaxWsServerFactoryBean();
    serverFactory.setAddress(BASE_URI);
    serverFactory.setServiceBean(new EchoImpl());
    final Server server = serverFactory.create();

    final JaxWsProxyFactoryBean clientFactory = new JaxWsProxyFactoryBean();
    clientFactory.setServiceClass(Echo.class);
    clientFactory.setAddress(BASE_URI);
    final Echo echo = (Echo) clientFactory.create();

    echo.echo(msg);

    // CXF Tracing span has no "component" tag, cannot use TestUtil.checkSpan()
    checkSpans(2);

    server.destroy();
    serverFactory.getBus().shutdown(true);
  }

  private static void checkSpans(int counts) {
    final Tracer tracer = GlobalTracer.get();
    if (tracer instanceof MockTracer) {
      final MockTracer mockTracer = (MockTracer) tracer;
      final List<MockSpan> spans = mockTracer.finishedSpans();
      if (counts != spans.size()) {
        throw new AssertionError("spans not matched counts");
      }
    }
  }
  
  @WebService
  public static interface Echo {
    String echo(String msg);
  }

  public static class EchoImpl implements Echo {
    @Override
    public String echo(String msg) {
      return msg;
    }
  }
}
