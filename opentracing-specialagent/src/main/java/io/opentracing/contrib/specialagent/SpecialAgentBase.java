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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

abstract class SpecialAgentBase {
  static final String CONFIG_ARG = "sa.config";
  static final String AGENT_RUNNER_ARG = "sa.agentrunner";
  static final String RULE_PATH_ARG = "sa.rulepath";
  static final String TRACER_PROPERTY = "sa.tracer";
  static final String LOG_EVENTS_PROPERTY = "sa.log.events";

  static final String DEPENDENCIES_TGF = "dependencies.tgf";
  static final String TRACER_FACTORY = "META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory";

  private static boolean propertiesLoaded = false;

  static void loadProperties() {
    if (propertiesLoaded)
      return;

    propertiesLoaded = true;
    final String configProperty = System.getProperty(CONFIG_ARG);
    try (
      final InputStream defaultConfig = SpecialAgentBase.class.getResourceAsStream("/default.properties");
      final BufferedReader userConfig = configProperty == null ? null : new BufferedReader(new FileReader(new File(configProperty)));
    ) {
      final Properties properties = new Properties();

      // Load default config properties
      properties.load(defaultConfig);

      // Load user config properties
      if (userConfig != null) {
        for (String line; (line = userConfig.readLine()) != null;) {
          final int eq = line.indexOf('=');
          if (eq == -1)
            properties.put(line.trim(), "");
          else
            properties.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
      }

      // Set config properties as system properties
      for (final Map.Entry<Object,Object> entry : properties.entrySet())
        if (System.getProperty((String)entry.getKey()) == null)
          System.setProperty((String)entry.getKey(), (String)entry.getValue());
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}