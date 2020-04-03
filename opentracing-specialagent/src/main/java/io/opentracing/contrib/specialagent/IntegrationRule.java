/* Copyright 2020 The OpenTracing Authors
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

import java.util.List;
import java.util.Objects;

public class IntegrationRule {
  private final PluginManifest pluginManifest;
  private final List<AgentRule> deferrers;
  private final List<AgentRule> agentRules;

  public IntegrationRule(final PluginManifest pluginManifest, final List<AgentRule> deferrers, final List<AgentRule> agentRules) {
    this.pluginManifest = Objects.requireNonNull(pluginManifest);
    this.deferrers = deferrers;
    this.agentRules = agentRules;
  }

  public PluginManifest getPluginManifest() {
    return pluginManifest;
  }

  public List<AgentRule> getDeferrers() {
    return deferrers;
  }

  public List<AgentRule> getAgentRules() {
    return agentRules;
  }
}