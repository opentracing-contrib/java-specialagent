package io.opentracing.contrib.specialagent.grpc;

import static org.junit.Assert.assertEquals;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import io.opentracing.contrib.grpc.gen.GreeterGrpc;
import io.opentracing.contrib.grpc.gen.GreeterGrpc.GreeterBlockingStub;
import io.opentracing.contrib.grpc.gen.HelloReply;
import io.opentracing.contrib.grpc.gen.HelloRequest;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
//@Config(isolateClassLoader = false)
public class GrpcTest {
  @Rule
  public GrpcServerRule grpcServer = new GrpcServerRule();

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(MockTracer tracer) {

//    ServerBuilder.forPort(123)
//        .addService(new GreeterImpl()).build();

//    grpcServer.getServiceRegistry()
//        .addService(new GreeterImpl().bindService());

    grpcServer.getServiceRegistry()
        .addService(new GreeterImpl());

    final ManagedChannel channel = grpcServer.getChannel();
    final GreeterBlockingStub greeterBlockingStub = GreeterGrpc.newBlockingStub(channel);

    final String message = greeterBlockingStub
        .sayHello(HelloRequest.newBuilder().setName("world").build()).getMessage();

    assertEquals("Hello world", message);

    System.out.println(tracer.finishedSpans());

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

}
