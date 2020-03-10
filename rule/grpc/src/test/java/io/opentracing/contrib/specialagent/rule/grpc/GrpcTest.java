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

package io.opentracing.contrib.specialagent.rule.grpc;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

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

@RunWith(AgentRunner.class)
public class GrpcTest {
  @Rule
  public final GrpcServerRule grpcServer = new GrpcServerRule();

  @BeforeClass
  public static void beforeClass() {
    // FIXME: During IDE or Maven execution, the lightstep plugin jar is present
    // FIXME: on the classpath for tests. The lightstep jar has an old version
    // FIXME: of GRPC, which breaks the fingerprint test for the GRPC Plugin.
    System.setProperty("sa.fingerprint.skip", "true");
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    grpcServer.getServiceRegistry().addService(new GreeterImpl());

    final ManagedChannel channel = grpcServer.getChannel();
    final GreeterBlockingStub greeterBlockingStub = GreeterGrpc.newBlockingStub(channel);
    final String message = greeterBlockingStub.sayHello(HelloRequest.newBuilder().setName("world").build()).getMessage();

    assertEquals("Hello world", message);
    await().atMost(15, TimeUnit.SECONDS).until(new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    }, equalTo(2));
    assertEquals(2, tracer.finishedSpans().size());
  }

  private static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
    @Override
    public void sayHello(final HelloRequest req, final StreamObserver<HelloReply> responseObserver) {
      // verify that there is an active span in case of using GlobalTracer:
      if (GlobalTracer.get().activeSpan() == null)
        throw new RuntimeException("no active span");

      final HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }
  }
}