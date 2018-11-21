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

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.logging.LogManager;

import org.jboss.byteman.agent.Main;

import com.sun.tools.attach.VirtualMachine;

/**
 * Provides a wrapper around the ByteMan agent, to establish required system
 * properties and the manager class.
 */
public class Agent {
  static {
    final String loggingConfig = System.getProperty("java.util.logging.config.file");
    if (loggingConfig != null) {
      try {
        LogManager.getLogManager().readConfiguration((loggingConfig.contains("file:/") ? new URL(loggingConfig) : new URL("file", "", loggingConfig)).openStream());
      }
      catch (final IOException e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }

  static Instrumentation instrumentation;

  public static void main(final String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: <PID>");
      System.exit(1);
    }

    final VirtualMachine vm = VirtualMachine.attach(args[0]);
    final String agentPath = Agent.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    try {
      vm.loadAgent(agentPath, null);
    }
    finally {
      vm.detach();
    }
  }

  public static void premain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    Agent.instrumentation = instrumentation;
    Main.premain(addManager(agentArgs), instrumentation);
  }

  public static void agentmain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    premain(agentArgs, instrumentation);
  }

  protected static String addManager(String agentArgs) {
    if (agentArgs == null || agentArgs.trim().isEmpty())
      agentArgs = "";
    else
      agentArgs += ",";

    agentArgs += "manager:" + Manager.class.getName();
    return agentArgs;
  }
}