/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class ByteBuddyManager extends Manager {
  private static final Logger logger = Logger.getLogger(ByteBuddyManager.class.getName());

  private Instrumentation instrumentation;

  ByteBuddyManager() {
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
    // Prepare the ClassLoader rule
    ClassLoaderAgent.premain(agentArgs, instrumentation);

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
        if (logger.isLoggable(Level.FINE))
          logger.fine("Installing plugin: " + line);

        try {
          final Class<?> agentClass = Class.forName(line, true, allPluginsClassLoader);
          final Method method = agentClass.getMethod("buildAgent", String.class);
          final AgentBuilder builder = (AgentBuilder)method.invoke(null, agentArgs);
          builder.with(new MainListener(index)).installOn(instrumentation);
        }
        catch (final NoSuchMethodException e) {
          logger.log(Level.SEVERE, "Method " + line + "#buildAgent(String) was not found", e);
        }
        catch (final InvocationTargetException e) {
          logger.log(Level.SEVERE, "Error initliaizing plugin", e);
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
    public void onDiscovery(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
    }

    @Override
    public void onTransformation(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final DynamicType dynamicType) {
      if (!Agent.linkPlugin(index, classLoader))
        throw new IllegalStateException("Disallowing transformation due to incompatibility");
    }

    @Override
    public void onIgnored(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
    }

    @Override
    public void onError(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final Throwable throwable) {
      logger.log(Level.SEVERE, "Error transforming " + typeName, throwable);
    }

    @Override
    public void onComplete(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
    }
  }
}