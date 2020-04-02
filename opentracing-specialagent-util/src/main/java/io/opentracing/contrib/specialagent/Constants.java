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

public final class Constants {
  public static final String CONFIG_ARG = "sa.config";
  public static final String AGENT_RUNNER_ARG = "sa.agentrunner";
  public static final String INIT_DEFER = "sa.init.defer";
  public static final String REWRITE_ARG = "sa.rewrite";
  public static final String EXPORTER_PROPERTY = "sa.exporter";
  public static final String LOG_EVENTS_PROPERTY = "sa.log.events";
  public static final String DEPENDENCIES_TGF = "dependencies.tgf";

  private Constants() {
  }
}