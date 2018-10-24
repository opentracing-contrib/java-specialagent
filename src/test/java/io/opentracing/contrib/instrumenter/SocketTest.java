package io.opentracing.contrib.instrumenter;

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
      final Thread child = new Thread() {
        @Override
        public void run() {
          try (final Socket socket = new Socket("127.0.0.1", port)) {
            final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            for (Request request; (request = (Request)in.readObject()) != null;)
              out.writeObject(new Response(request.getMethodName(), null, false, null));
          }
          catch (final ClassNotFoundException | IOException e) {
            throw new IllegalStateException(e);
          }
        }
      };

      child.start();
      final Socket socket = serverSocket.accept();
      final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

      for (final String string : new String[] {"foo", "bar", "test1", "test2", "test3", "test4"}) {
        out.writeObject(new Request(string));
        final Response response = (Response)in.readObject();
        assertEquals(string, response.getMethodName());
      }

      out.writeObject(null);
    }
  }
}