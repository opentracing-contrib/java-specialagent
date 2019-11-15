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

package io.opentracing.contrib.specialagent.test.grpc;

import java.io.IOException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentracing.contrib.grpc.gen.GreeterGrpc;
import io.opentracing.contrib.grpc.gen.GreeterGrpc.GreeterBlockingStub;
import io.opentracing.contrib.grpc.gen.HelloReply;
import io.opentracing.contrib.grpc.gen.HelloRequest;
import io.opentracing.contrib.specialagent.TestUtil;

public class GrpcITest {
  public static void main(final String[] args) throws IOException {
    TestUtil.initTerminalExceptionHandler();
    final Server server = ServerBuilder.forPort(8086).addService(new GreeterImpl()).build().start();
    final ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8086).usePlaintext(true).build();
    final GreeterBlockingStub greeterBlockingStub = GreeterGrpc.newBlockingStub(channel);

    greeterBlockingStub.sayHello(HelloRequest.newBuilder().setName("world").build()).getMessage();
    server.shutdownNow();

    TestUtil.checkSpan("java-grpc", 2);
  }

  private static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
    @Override
    public void sayHello(final HelloRequest req, final StreamObserver<HelloReply> responseObserver) {
      TestUtil.checkActiveSpan();
      final HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }
  }
}