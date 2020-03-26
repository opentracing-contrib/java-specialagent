/* Copyright 2020 The OpenTracing Authors
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

/**
 * When run from an {@code AgentRunner}, {@link AgentRule}s that are loaded
 * earlier than {@link ClassLoaderAgentRule} result in inconvenient class
 * loading circumstances that end up creating multiple static instances of
 * {@link Logger}. To avoid this, the {@link DefaultAgentRule} provides a local
 * logging stub.
 *
 * @author Seva Safris
 */
public abstract class DefaultAgentRule extends AgentRule {
  public enum DefaultLevel {
    SEVERE,
    FINE,
    FINEST
  }

  private static Boolean isAgentRunner;
  private static Logger logger;

  public static void log(final String message, final Throwable thrown, final DefaultLevel level) {
    if (isAgentRunner == null ? isAgentRunner = Adapter.isAgentRunner() : isAgentRunner) {
      final String logLevel = System.getProperty("sa.log.level");
      if (level == DefaultLevel.SEVERE || logLevel != null && logLevel.startsWith("FINE")) {
        System.err.println(message);
        if (thrown != null)
          thrown.printStackTrace(System.err);
      }
    }
    else {
      if (logger == null)
        logger = Logger.getLogger(ClassLoaderAgentRule.class);

      if (level == DefaultLevel.SEVERE)
        logger.log(Level.SEVERE, message, thrown);
      else if (level == DefaultLevel.FINE && logger.isLoggable(Level.FINE))
        logger.log(Level.FINE, message, thrown);
      else if (level == DefaultLevel.FINEST && logger.isLoggable(Level.FINEST))
        logger.log(Level.FINEST, message, thrown);
    }
  }
}