package io.opentracing.contrib.specialagent.rule;

import java.io.IOException;
import java.net.ServerSocket;

public class CommonHelper {

    public static int getUnusedPort() {
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            serverSocket.close();
            return port;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
