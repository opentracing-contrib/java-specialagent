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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import custom.Address;
import custom.CustomService;
import custom.CustomService.AsyncClient;
import custom.User;
import custom.UserWithAddress;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracerTestUtil;
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

@RunWith(AgentRunner.class)
public class ThriftTest {
  private TServer server;
  private static int port = 8883;

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
    port++;
  }

  @After
  public void after() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void test(MockTracer tracer) throws Exception {
    startNewThreadPoolServer();

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);

    CustomService.Client client = new CustomService.Client(protocol);
    assertEquals("Say Good bye World", client.say("Good bye", "World"));

    await().atMost(5, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    assertNull(tracer.activeSpan());
  }

  @Test
  public void withoutArgs(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    assertEquals("no args", client.withoutArgs());

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
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

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    try {
      assertEquals("Say Good bye", client.withError());
      fail();
    } catch (Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
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

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    assertEquals("collision", client.withCollision("collision"));

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
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

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    client.oneWayWithError();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
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

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    client.oneWay();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
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

    Factory protocolFactory = new Factory();

    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient
        .say("Async", "World", new AsyncMethodCallback<String>() {
          @Override
          public void onComplete(String response) {
            assertEquals("Say Async World", response);
            assertNotNull(GlobalTracer.get().activeSpan());
            counter.incrementAndGet();
          }

          @Override
          public void onError(Exception exception) {
            exception.printStackTrace();
          }
        });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    assertEquals(1, counter.get());

    List<MockSpan> spans = tracer.finishedSpans();
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

    Factory protocolFactory = new Factory();

    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient
        .withoutArgs(new AsyncMethodCallback<String>() {
          @Override
          public void onComplete(String response) {
            assertEquals("no args", response);
            assertNotNull(GlobalTracer.get().activeSpan());
            counter.incrementAndGet();
          }

          @Override
          public void onError(Exception exception) {
            exception.printStackTrace();
          }
        });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    assertEquals(1, counter.get());

    List<MockSpan> spans = tracer.finishedSpans();
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
    for (int i = 0; i < 4; i++) {
      Factory protocolFactory = new Factory();

      TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
      TAsyncClientManager clientManager = new TAsyncClientManager();
      AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
      asyncClient
          .withDelay(1, new AsyncMethodCallback<String>() {
            @Override
            public void onComplete(String response) {
              assertEquals("delay 1", response);
              assertNotNull(GlobalTracer.get().activeSpan());
              counter.incrementAndGet();
            }

            @Override
            public void onError(Exception exception) {
              exception.printStackTrace();
            }
          });
    }
    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(8));
    assertEquals(4, counter.get());

    List<MockSpan> spans = tracer.finishedSpans();
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

    Factory protocolFactory = new Factory();

    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient
        .oneWay(new AsyncMethodCallback<Void>() {
          @Override
          public void onComplete(Void response) {
            assertNotNull(GlobalTracer.get().activeSpan());
            counter.incrementAndGet();
          }

          @Override
          public void onError(Exception exception) {
            exception.printStackTrace();
          }
        });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    assertEquals(1, counter.get());

    List<MockSpan> spans = tracer.finishedSpans();
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

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    User user = new User("name32", 30);
    Address address = new Address("line", "City", "1234AB");

    UserWithAddress userWithAddress = client.save(user, address);

    assertEquals(user, userWithAddress.user);
    assertEquals(address, userWithAddress.address);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    List<MockSpan> mockSpans = tracer.finishedSpans();
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

    for (int i = 0; i < 4; i++) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            TTransport transport = new TSocket("localhost", port);
            transport.open();

            TProtocol protocol = new TBinaryProtocol(transport);
            CustomService.Client client = new CustomService.Client(protocol);

            assertEquals("delay 1", client.withDelay(1));
            client.oneWay();
          } catch (Exception e) {
            e.printStackTrace();
            fail();
          }
        }
      }).start();
    }
    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(16));

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(16, spans.size());

    assertNull(tracer.activeSpan());
    verify(tracer, times(8)).inject(any(SpanContext.class), any(Format.class), any());
  }

  @Test
  public void withParent(MockTracer tracer) throws Exception {
    tracer = spy(tracer);
    GlobalTracerTestUtil.setGlobalTracerUnconditionally(tracer);

    startNewThreadPoolServer();

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    Scope parent = tracer.buildSpan("parent").startActive(true);
    MockSpan parentSpan = (MockSpan) tracer.activeSpan();

    assertEquals("Say one two", client.say("one", "two"));
    assertEquals("Say three four", client.say("three", "four"));
    client.oneWay();
    assertEquals("no args", client.withoutArgs());

    parent.close();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(9));

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(9, spans.size());
    for (MockSpan span : spans) {
      assertEquals(parentSpan.context().traceId(), span.context().traceId());
    }

    List<MockSpan> clientSpans = getClientSpans(spans);
    assertEquals(4, clientSpans.size());
    for (MockSpan clientSpan : clientSpans) {
      assertEquals(parentSpan.context().spanId(), clientSpan.parentId());
    }

    assertNull(tracer.activeSpan());
    verify(tracer, times(4)).inject(any(SpanContext.class), any(Format.class), any());
  }

  private List<MockSpan> getClientSpans(List<MockSpan> spans) {
    List<MockSpan> res = new ArrayList<>();
    for (MockSpan span : spans) {
      Object spanKind = span.tags().get(Tags.SPAN_KIND.getKey());
      if (Tags.SPAN_KIND_CLIENT.equals(spanKind)) {
        res.add(span);
      }
    }
    return res;
  }

  private void startNewThreadPoolServer() throws Exception {
    TServerTransport transport = new TServerSocket(port);
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    //TTransportFactory transportFactory = new TFramedTransport.Factory();

    CustomHandler customHandler = new CustomHandler();
    final TProcessor customProcessor = new CustomService.Processor<CustomService.Iface>(
        customHandler);

    TThreadPoolServer.Args args = new TThreadPoolServer.Args(transport)
        .processorFactory(new TProcessorFactory(customProcessor))
        .protocolFactory(protocolFactory)
        //.transportFactory(transportFactory)
        .minWorkerThreads(5)
        //.executorService(Executors.newCachedThreadPool())
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
    CustomHandler customHandler = new CustomHandler();
    final TProcessor customProcessor = new CustomService.Processor<CustomService.Iface>(
        customHandler);
    TNonblockingServerSocket tnbSocketTransport = new TNonblockingServerSocket(port, 30000);
    TNonblockingServer.Args tnbArgs = new TNonblockingServer.Args(tnbSocketTransport);
    tnbArgs.processor(customProcessor);

    server = new TNonblockingServer(tnbArgs);
    new Thread(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    }).start();
  }


  private Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }
}
