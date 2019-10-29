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

package io.opentracing.contrib.specialagent.rule.rabbitmq.client;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.SystemLauncherListener;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.SystemConfig;

class EmbeddedAMQPBroker {
  private int brokerPort;
  private final SystemLauncher broker = new SystemLauncher(new SystemLauncherListener() {
    private SystemConfig<?> systemConfig;

    @Override
    public void onContainerResolve(final SystemConfig<?> systemConfig) {
      this.systemConfig = systemConfig;
    }

    @Override
    public void beforeStartup() {
    }

    @Override
    public void errorOnStartup(final RuntimeException e) {
    }

    @Override
    public void afterStartup() {
      brokerPort = systemConfig.getContainer().getChildByName(Port.class, "AMQP").getBoundPort();
    }

    @Override
    public void onContainerClose(final SystemConfig<?> systemConfig) {
    }

    @Override
    public void onShutdown(final int exitCode) {
    }

    @Override
    public void exceptionOnShutdown(final Exception e) {
    }
  });

  EmbeddedAMQPBroker() throws Exception {
    final Map<String,Object> context = new HashMap<>();
    context.put("qpid.amqp_port", 0);
    context.put("qpid.work_dir", Files.createTempDirectory("qpid").toFile().getAbsolutePath());

    final Map<String,Object> brokerOptions = new HashMap<>();
    brokerOptions.put("type", "Memory");
    brokerOptions.put("context", context);
    brokerOptions.put("initialConfigurationLocation", Thread.currentThread().getContextClassLoader().getResource("qpid-config.json").getPath());

    // start broker
    broker.startup(brokerOptions);
  }

  void shutdown() {
    broker.shutdown();
    new File("derby.log").delete();
  }

  int getBrokerPort() {
    return brokerPort;
  }
}