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

package io.opentracing.contrib.specialagent.rule.mule4.module.artifact;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.mule.runtime.module.artifact.api.classloader.FineGrainedControlClassLoader;
import org.mule.runtime.module.artifact.api.classloader.LookupStrategy;

public class FineGrainedControlAgentIntercept {
  private static final String CLS_SFX = ".class";
  private static final Logger logger = Logger.getLogger(FilteringArtifactAgentIntercept.class.getName());

  public static Object exit(final Object thiz, final Object returned, final Object resObj) {
    final String resName = (String)resObj;

    // if it's a class that wasn't found then we continue
    if (returned != null || !resName.endsWith(CLS_SFX))
      return returned;

    final String clazzName = resName.substring(0, resName.length() - CLS_SFX.length()).replace('/', '.');

    // @see https://github.com/mulesoft/mule/blob/mule-4.2.2/modules/artifact/src/main/java/org/mule/runtime/module/artifact/api/classloader/FineGrainedControlClassLoader.java#L67
    final FineGrainedControlClassLoader loader = (FineGrainedControlClassLoader)thiz;
    final LookupStrategy lookupStrategy = loader.getClassLoaderLookupPolicy().getClassLookupStrategy(clazzName);

    if (lookupStrategy == null)
      return null;

    Object result;
    for (final ClassLoader classLoader : lookupStrategy.getClassLoaders(loader)) {
      if (classLoader != thiz) {
        if (classLoader != null)
          result = classLoader.getResource(resName);
        else
          result = ClassLoader.getSystemResource(resName);

        if (result != null)
          return result;

        if (logger.isLoggable(Level.FINE))
          logger.fine("Could not locate resource " + resName + " with strategy " + lookupStrategy + " in " + thiz);
      }
    }

    return null;
  }
}