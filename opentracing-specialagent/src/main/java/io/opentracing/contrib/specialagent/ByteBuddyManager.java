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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class ByteBuddyTransformer extends Transformer {
  private static final Logger logger = Logger.getLogger(ByteBuddyTransformer.class.getName());

  private Instrumentation instrumentation;

  ByteBuddyTransformer() {
    super("otaplugins.txt");
  }

  @Override
  void premain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    this.instrumentation = instrumentation;
    Agent.initialize();
  }

  /**
   * This method loads any OpenTracing Agent rules (otarules.btm) found as
   * resources within the supplied classloader.
   */
  @Override
  void loadRules(final ClassLoader allPluginsClassLoader, final Map<String,Integer> pluginJarToIndex, final String agentArgs) throws IOException {
    // Prepare the Plugin rules
    final Enumeration<URL> enumeration = allPluginsClassLoader.getResources(file);
    while (enumeration.hasMoreElements()) {
      final URL scriptUrl = enumeration.nextElement();
      final String pluginJar = Util.getSourceLocation(scriptUrl, file);
      if (logger.isLoggable(Level.FINEST))
        logger.finest("Dereferencing index for " + pluginJar);

      final int index = pluginJarToIndex.get(pluginJar);

      final BufferedReader reader = new BufferedReader(new InputStreamReader(scriptUrl.openStream()));
      for (String line; (line = reader.readLine()) != null;) {
        try {
          final Class<?> agentClass = Class.forName(line, false, allPluginsClassLoader);
          final Method method = agentClass.getMethod("premain", String.class, Instrumentation.class);
          final AgentBuilder builder = (AgentBuilder)method.invoke(null, agentArgs, instrumentation);
          builder
            .with(RedefinitionStrategy.RETRANSFORMATION)
            .with(InitializationStrategy.NoOp.INSTANCE)
            .with(TypeStrategy.Default.REDEFINE)
            .with(new MainListener(index))
            .installOn(instrumentation);
        }
        catch (final NoSuchMethodException e) {
          logger.severe("Method " + line + "#buildAgent(String) was not found");
        }
        catch (final InvocationTargetException e) {
          logger.log(Level.SEVERE, "Error invoking AgentBuilder", e);
        }
        catch (final ClassNotFoundException | IllegalAccessException e) {
          throw new IllegalStateException(e);
        }
      }
    }
  }

  @Override
  boolean disableTriggers() {
    return false;
  }

  @Override
  boolean enableTriggers() {
    return false;
  }

  class MainListener implements AgentBuilder.Listener {
    private final int index;

    MainListener(final int index) {
      this.index = index;
    }

    @Override
    public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
    }

    @Override
    public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
      if (!Agent.linkPlugin(index, classLoader))
        throw new IllegalStateException("Disallowing transformation due to incompatibility");
    }

    @Override
    public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {
    }

    @Override
    public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
    }

    @Override
    public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
    }
  }
}