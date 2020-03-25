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

package io.opentracing.contrib.specialagent.test.cxf;

import java.util.List;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.test.cxf.interceptors.AbstractSpanTagInterceptor;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockSpan.LogEntry;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

public class CXFITest {
  private static final String BASE_URI = "http://127.0.0.1:48080";

  public static void main(final String[] args) {
    System.setProperty("sa.instrumentation.plugin.cxf.interceptors.client.in",
        "io.opentracing.contrib.specialagent.test.cxf.interceptors.ClientSpanTagInterceptor");
    System.setProperty("sa.instrumentation.plugin.cxf.interceptors.server.out",
        "io.opentracing.contrib.specialagent.test.cxf.interceptors.ServerSpanTagInterceptor");
    System.setProperty("sa.instrumentation.plugin.cxf.interceptors.classpath",
        "taget/test-classes");

    final String msg = "hello";

    final JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
    serverFactory.setAddress(BASE_URI);
    serverFactory.setServiceBean(new EchoImpl());
    final Server server = serverFactory.create();

    final JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
    clientFactory.setServiceClass(Echo.class);
    clientFactory.setAddress(BASE_URI);
    final Echo echo = clientFactory.create(Echo.class);

    echo.echo(msg);

    // CXF Tracing span has no "component" tag, cannot use TestUtil.checkSpan()
    checkSpans(2);
    checkTag();

    server.destroy();
    serverFactory.getBus().shutdown(true);
  }

  private static void checkTag() {
    final Tracer tracer = TestUtil.getGlobalTracer();
    if (tracer instanceof MockTracer) {
      final MockTracer mockTracer = (MockTracer) tracer;
      final List<MockSpan> spans = mockTracer.finishedSpans();
      for (final MockSpan span : spans) {
        if (!AbstractSpanTagInterceptor.SPAN_TAG_VALUE
            .equals(span.tags().get(AbstractSpanTagInterceptor.SPAN_TAG_KEY))) {
          throw new AssertionError("no costomized tag");
        }
      }
    }
  }

  private static void checkSpans(int counts) {
    final Tracer tracer = TestUtil.getGlobalTracer();
    if (tracer instanceof MockTracer) {
      final MockTracer mockTracer = (MockTracer) tracer;
      final List<MockSpan> spans = mockTracer.finishedSpans();
      int matchSpans = 0;
      for (final MockSpan span : spans) {
        printSpan(span);
        if (span.tags().get(Tags.COMPONENT.getKey()) == null) {
          ++matchSpans;
        }
      }

      if (counts != matchSpans)
        throw new AssertionError("spans not matched counts");
    }
  }

  private static void printSpan(final MockSpan span) {
    System.out.println("Span: " + span);
    System.out.println("\tComponent: " + span.tags().get(Tags.COMPONENT.getKey()));
    System.out.println("\tTags: " + span.tags());
    System.out.println("\tLogs: ");
    for (final LogEntry logEntry : span.logEntries())
      System.out.println("\t" + logEntry.fields());
  }

  @Path("/")
  public static interface Echo {
    @POST
    String echo(String msg);
  }

  public static class EchoImpl implements Echo {
    @Override
    public String echo(final String msg) {
      return msg;
    }
  }
}
