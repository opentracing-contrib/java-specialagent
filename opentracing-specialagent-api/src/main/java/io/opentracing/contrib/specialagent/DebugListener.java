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

import java.util.logging.Logger;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * An {@link net.bytebuddy.agent.builder.AgentBuilder.Listener} used for
 * debugging of ByteBuddy's transformation calls.
 *
 * @author Seva Safris
 */
public class DebugListener implements AgentBuilder.Listener {
  public static final Logger logger = Logger.getLogger(DebugListener.class.getName());

  @Override
  public void onDiscovery(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
//     logger.info("onDiscovery:" + typeName);
  }

  @Override
  public void onTransformation(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final DynamicType dynamicType) {
//     logger.info("onTransformation:" + typeDescription);
  }

  @Override
  public void onIgnored(final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
//     logger.info("onIgnored:" + typeDescription);
  }

  @Override
  public void onError(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded, final Throwable throwable) {
    logger.info("onError:" + typeName);
    throwable.printStackTrace();
  }

  @Override
  public void onComplete(final String typeName, final ClassLoader classLoader, final JavaModule module, final boolean loaded) {
//     logger.info("onComplete:" + typeName);
  }
}