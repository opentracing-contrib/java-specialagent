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

import static io.opentracing.contrib.specialagent.Constants.*;

public abstract class Adapter {
  public static boolean isAgentRunner() {
    return System.getProperty(AGENT_RUNNER_ARG) != null;
  }

  public static ClassLoader tracerClassLoader;

  public abstract Object getAgentRunnerTracer();
  public abstract String[] loadTracer(ClassLoader pluginsClassLoader, ClassLoader isoClassLoader) throws IllegalStateException;
}