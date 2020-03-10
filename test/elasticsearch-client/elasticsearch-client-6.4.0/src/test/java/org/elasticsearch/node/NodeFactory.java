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

package org.elasticsearch.node;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.function.Supplier;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.plugins.Plugin;

public class NodeFactory {
  private static Environment makeEnvironment(final Settings settings) {
    try {
      for (final Method method : InternalSettingsPreparer.class.getMethods()) {
        if (Modifier.isStatic(method.getModifiers()) && "prepareEnvironment".equals(method.getName())) {
          if (method.getParameterCount() == 2)
            return (Environment)method.invoke(null, settings, null);

          if (method.getParameterCount() == 4 && Supplier.class.equals(method.getParameterTypes()[3])) {
            return (Environment)method.invoke(null, settings, new HashMap<>(), null, new Supplier<String>() {
              @Override
              public String get() {
                return "local";
              }
            });
          }
        }
      }

      throw new IllegalStateException("Could not find compatible method: InternalSettingsPreparer.prepareEnvironment(...)");
    }
    catch (final IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static Node makeNode(final Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
    final Environment environment = makeEnvironment(settings);
    try {
      for (final Constructor<?> constructor : Node.class.getDeclaredConstructors()) {
        if (Arrays.equals(constructor.getParameterTypes(), new Class[] {Environment.class, Collection.class}))
          return (Node)constructor.newInstance(environment, classpathPlugins);

        if (Arrays.equals(constructor.getParameterTypes(), new Class[] {Environment.class, Collection.class, boolean.class}))
          return (Node)constructor.newInstance(environment, classpathPlugins, false);
      }

      throw new IllegalStateException("Could not find compatible method: Node.<init>(...)");
    }
    catch (final IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}