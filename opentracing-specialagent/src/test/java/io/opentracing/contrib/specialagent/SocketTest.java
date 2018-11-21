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

package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.Test;

public class SocketTest {
  @Test
  public void test() throws ClassNotFoundException, IOException {
    try (final ServerSocket serverSocket = new ServerSocket(0)) {
      final int port = serverSocket.getLocalPort();
      new Thread() {
        @Override
        public void run() {
          try (final Socket socket = new Socket("127.0.0.1", port)) {
            final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            for (TestResult request; (request = (TestResult)in.readObject()) != null;)
              out.writeObject(new TestResult(request.getMethodName(), null));
          }
          catch (final ClassNotFoundException | IOException e) {
            throw new IllegalStateException(e);
          }
        }
      }.start();

      final Socket socket = serverSocket.accept();
      final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

      for (final String string : new String[] {"foo", "bar", "test1", "test2", "test3", "test4"}) {
        out.writeObject(new TestResult(string, null));
        final TestResult response = (TestResult)in.readObject();
        assertEquals(string, response.getMethodName());
      }

      out.writeObject(null);
    }
  }
}