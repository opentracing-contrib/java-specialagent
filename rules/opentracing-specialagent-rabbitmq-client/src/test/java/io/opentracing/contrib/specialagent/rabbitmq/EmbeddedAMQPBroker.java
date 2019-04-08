/* Copyright 2018-2019 The OpenTracing Authors
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
package io.opentracing.contrib.specialagent.rabbitmq;


import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.net.ServerSocketFactory;
import org.apache.qpid.server.SystemLauncher;


class EmbeddedAMQPBroker {

  private int brokerPort;
  private final SystemLauncher broker = new SystemLauncher();

  EmbeddedAMQPBroker() throws Exception {
    this.brokerPort = findAvailableTcpPort();
    final String configFileName = "qpid-config.json";
    Map<String, Object> brokerOptions = new HashMap<>();
    brokerOptions.put("type", "Memory");
    Map<String, Object> context = new HashMap<>();
    context.put("qpid.amqp_port", brokerPort);
    context.put("qpid.work_dir", Files.createTempDirectory("qpid").toFile().getAbsolutePath());
    brokerOptions.put("context", context);
    brokerOptions.put("initialConfigurationLocation", findResourcePath(configFileName));
    // start broker
    broker.startup(brokerOptions);
  }

  void shutdown() {
    broker.shutdown();
    new File("derby.log").delete();
  }

  private String findResourcePath(final String file) {
    return "src/test/resources/" + file;
  }


  private static int findAvailableTcpPort() {
    for (int i = 1024; i < 65535; i++) {
      if (isPortAvailable(i)) {
        return i;
      }
    }
    throw new IllegalStateException("No port available");
  }

  private static boolean isPortAvailable(int port) {
    try {
      ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket(
          port, 1, InetAddress.getByName("localhost"));
      serverSocket.close();
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  int getBrokerPort() {
    return brokerPort;
  }
}
