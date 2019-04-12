package io.opentracing.contrib.specialagent.grpc;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config;
import io.opentracing.contrib.specialagent.grpc.gen.GreeterGrpc;
import io.opentracing.contrib.specialagent.grpc.gen.GreeterGrpc.GreeterBlockingStub;
import io.opentracing.contrib.specialagent.grpc.gen.HelloReply;
import io.opentracing.contrib.specialagent.grpc.gen.HelloRequest;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@Config(isolateClassLoader = false)
public class GrpcTest {
  @Rule
  public GrpcServerRule grpcServer = new GrpcServerRule();

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(MockTracer tracer) {
    grpcServer.getServiceRegistry()
        .addService(new GreeterImpl());

    ManagedChannel channel = grpcServer.getChannel();
    GreeterBlockingStub greeterBlockingStub = GreeterGrpc.newBlockingStub(channel);

    String message = greeterBlockingStub
        .sayHello(HelloRequest.newBuilder().setName("world").build()).getMessage();

    assertEquals("Hello world", message);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));

    assertEquals(2, tracer.finishedSpans().size());
  }

  private static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

    @Override
    public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
      // verify that there is an active span in case of using GlobalTracer:
      if (GlobalTracer.get().activeSpan() == null) {
        throw new RuntimeException("no active span");
      }

      HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }
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
