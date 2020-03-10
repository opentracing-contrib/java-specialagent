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

package io.opentracing.contrib.specialagent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

abstract class SpecialAgentBase {
  static final String CONFIG_ARG = "sa.config";
  static final String AGENT_RUNNER_ARG = "sa.agentrunner";
  static final String INIT_DEFER = "sa.init.defer";
  static final String RULE_PATH_ARG = "sa.rulepath";
  static final String TRACER_PROPERTY = "sa.tracer";
  static final String LOG_EVENTS_PROPERTY = "sa.log.events";

  static final String DEPENDENCIES_TGF = "dependencies.tgf";
  static final String TRACER_FACTORY = "META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory";

  private static boolean propertiesLoaded = false;

  private static void loadProperties(final Map<String,String> properties, final BufferedReader reader) throws IOException {
    for (String line; (line = reader.readLine()) != null;) {
      line = line.trim();
      char ch;
      if (line.length() == 0 || (ch = line.charAt(0)) == '#' || ch == '!')
        continue;

      final int eq = line.indexOf('=');
      if (eq == -1) {
        properties.put(line, "");
      }
      else if (eq > 0) {
        final String key = line.substring(0, eq).trim();
        final String value = line.substring(eq + 1).trim();
        if (key.length() > 0)
          properties.put(key, value);
      }
    }
  }

  static void loadProperties() {
    if (propertiesLoaded)
      return;

    propertiesLoaded = true;
    final String configProperty = System.getProperty(CONFIG_ARG);
    try (
      final InputStream defaultConfig = SpecialAgentBase.class.getResourceAsStream("/default.properties");
      final FileReader userConfig = configProperty == null ? null : new FileReader(configProperty);
    ) {
      final Map<String,String> properties = new HashMap<>();

      // Load default config properties
      loadProperties(properties, new BufferedReader(new InputStreamReader(defaultConfig)));

      // Load user config properties
      if (userConfig != null)
        loadProperties(properties, new BufferedReader(userConfig));

      // Set config properties as system properties
      for (final Map.Entry<String,String> entry : properties.entrySet())
        if (System.getProperty(entry.getKey()) == null)
          System.setProperty(entry.getKey(), entry.getValue());

      Logger.refreshLoggers();
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}