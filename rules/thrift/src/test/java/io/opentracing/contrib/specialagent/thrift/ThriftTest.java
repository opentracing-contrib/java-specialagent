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

package io.opentracing.contrib.specialagent.thrift;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.thrift.TProcessor;
import org.apache.thrift.TProcessorFactory;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TBinaryProtocol.Factory;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.thrift.gen.Address;
import io.opentracing.contrib.specialagent.thrift.gen.CustomHandler;
import io.opentracing.contrib.specialagent.thrift.gen.CustomService;
import io.opentracing.contrib.specialagent.thrift.gen.CustomService.AsyncClient;
import io.opentracing.contrib.specialagent.thrift.gen.User;
import io.opentracing.contrib.specialagent.thrift.gen.UserWithAddress;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracerTestUtil;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class ThriftTest {
  private TServer server;
  private static int port = 8883;

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
    ++port;
  }

  @After
  public void after() {
    if (server != null)
      server.stop();
  }

  @Test
  public void test(final MockTracer tracer) throws Exception {
    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);

    final CustomService.Client client = new CustomService.Client(protocol);
    assertEquals("Say Good bye World", client.say("Good bye", "World"));

    await().atMost(5, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);
    assertNull(tracer.activeSpan());
  }

  @Test
  public void withoutArgs(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    assertEquals("no args", client.withoutArgs());

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    assertNull(tracer.activeSpan());

    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void withError(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    try {
      assertEquals("Say Good bye", client.withError());
      fail();
    }
    catch (final Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    assertNull(tracer.activeSpan());

    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void withCollision(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);
    final CustomService.Client client = new CustomService.Client(protocol);

    assertEquals("collision", client.withCollision("collision"));

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void oneWayWithError(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    client.oneWayWithError();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void oneWay(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    client.oneWay();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void async(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startAsyncServer();

    final Factory protocolFactory = new Factory();
    final TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    final TAsyncClientManager clientManager = new TAsyncClientManager();
    final AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient.say("Async", "World", new AsyncMethodCallback<String>() {
      @Override
      public void onComplete(final String response) {
        assertEquals("Say Async World", response);
        assertNotNull(GlobalTracer.get().activeSpan());
        counter.incrementAndGet();
      }

      @Override
      public void onError(final Exception exception) {
        exception.printStackTrace();
      }
    });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    assertEquals(1, counter.get());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());

    assertNull(tracer.activeSpan());
    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void asyncWithoutArgs(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startAsyncServer();

    final Factory protocolFactory = new Factory();

    final TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    final TAsyncClientManager clientManager = new TAsyncClientManager();
    final AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient.withoutArgs(new AsyncMethodCallback<String>() {
      @Override
      public void onComplete(final String response) {
        assertEquals("no args", response);
        assertNotNull(GlobalTracer.get().activeSpan());
        counter.incrementAndGet();
      }

      @Override
      public void onError(final Exception exception) {
        exception.printStackTrace();
      }
    });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    assertEquals(1, counter.get());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());

    assertNull(tracer.activeSpan());
    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void asyncMany(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startAsyncServer();

    final AtomicInteger counter = new AtomicInteger();
    for (int i = 0; i < 4; ++i) {
      final Factory protocolFactory = new Factory();
      final TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
      final TAsyncClientManager clientManager = new TAsyncClientManager();
      final AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
      asyncClient.withDelay(1, new AsyncMethodCallback<String>() {
        @Override
        public void onComplete(final String response) {
          assertEquals("delay 1", response);
          assertNotNull(GlobalTracer.get().activeSpan());
          counter.incrementAndGet();
        }

        @Override
        public void onError(final Exception exception) {
          exception.printStackTrace();
        }
      });
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(8));
    assertEquals(4, counter.get());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(8, spans.size());

    assertNull(tracer.activeSpan());
    verify(tracer, times(8)).buildSpan(anyString());
    verify(tracer, times(4)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void oneWayAsync(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startAsyncServer();

    final Factory protocolFactory = new Factory();
    final TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    final TAsyncClientManager clientManager = new TAsyncClientManager();
    final AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient.oneWay(new AsyncMethodCallback<Void>() {
      @Override
      public void onComplete(final Void response) {
        assertNotNull(GlobalTracer.get().activeSpan());
        counter.incrementAndGet();
      }

      @Override
      public void onError(final Exception exception) {
        exception.printStackTrace();
      }
    });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    assertEquals(1, counter.get());

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());

    assertNull(tracer.activeSpan());
    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void withStruct(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);
    final CustomService.Client client = new CustomService.Client(protocol);

    final User user = new User("name32", 30);
    final Address address = new Address("line", "City", "1234AB");

    final UserWithAddress userWithAddress = client.save(user, address);

    assertEquals(user, userWithAddress.user);
    assertEquals(address, userWithAddress.address);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    final List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    verify(tracer, times(2)).buildSpan(anyString());
    verify(tracer, times(1)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void manyCallsParallel(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    for (int i = 0; i < 4; ++i) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            final TTransport transport = new TSocket("localhost", port);
            transport.open();

            final TProtocol protocol = new TBinaryProtocol(transport);
            CustomService.Client client = new CustomService.Client(protocol);

            assertEquals("delay 1", client.withDelay(1));
            client.oneWay();
          }
          catch (final Exception e) {
            e.printStackTrace();
            fail();
          }
        }
      }).start();
    }
    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(16));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(16, spans.size());

    assertNull(tracer.activeSpan());
    verify(tracer, times(8)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void withParent(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    final TTransport transport = new TSocket("localhost", port);
    transport.open();

    final TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    final Scope parent = tracer.buildSpan("parent").startActive(true);
    MockSpan parentSpan = (MockSpan) tracer.activeSpan();

    assertEquals("Say one two", client.say("one", "two"));
    assertEquals("Say three four", client.say("three", "four"));
    client.oneWay();
    assertEquals("no args", client.withoutArgs());

    parent.close();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(9));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(9, spans.size());
    for (final MockSpan span : spans) {
      assertEquals(parentSpan.context().traceId(), span.context().traceId());
    }

    final List<MockSpan> clientSpans = getClientSpans(spans);
    assertEquals(4, clientSpans.size());
    for (final MockSpan clientSpan : clientSpans) {
      assertEquals(parentSpan.context().spanId(), clientSpan.parentId());
    }

    assertNull(tracer.activeSpan());
    verify(tracer, times(4)).inject(any(SpanContext.class), any(Format.class), any());
  }

  private static List<MockSpan> getClientSpans(final List<MockSpan> spans) {
    final List<MockSpan> res = new ArrayList<>();
    for (final MockSpan span : spans) {
      final Object spanKind = span.tags().get(Tags.SPAN_KIND.getKey());
      if (Tags.SPAN_KIND_CLIENT.equals(spanKind))
        res.add(span);
    }

    return res;
  }

  private void startNewThreadPoolServer() throws Exception {
    final TServerTransport transport = new TServerSocket(port);
    final TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    // TTransportFactory transportFactory = new TFramedTransport.Factory();

    final CustomHandler customHandler = new CustomHandler();
    final TProcessor customProcessor = new CustomService.Processor<CustomService.Iface>(customHandler);

    final TThreadPoolServer.Args args = new TThreadPoolServer.Args(transport)
      .processorFactory(new TProcessorFactory(customProcessor))
      .protocolFactory(protocolFactory)
      // .transportFactory(transportFactory)
      .minWorkerThreads(5)
      // .executorService(Executors.newCachedThreadPool())
      .maxWorkerThreads(10);

    server = new TThreadPoolServer(args);

    new Thread(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    }).start();
  }

  private void startAsyncServer() throws Exception {
    final CustomHandler customHandler = new CustomHandler();
    final TProcessor customProcessor = new CustomService.Processor<CustomService.Iface>(customHandler);
    final TNonblockingServerSocket tnbSocketTransport = new TNonblockingServerSocket(port, 30000);
    final TNonblockingServer.Args tnbArgs = new TNonblockingServer.Args(tnbSocketTransport);
    tnbArgs.processor(customProcessor);

    server = new TNonblockingServer(tnbArgs);
    new Thread(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    }).start();
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }
}