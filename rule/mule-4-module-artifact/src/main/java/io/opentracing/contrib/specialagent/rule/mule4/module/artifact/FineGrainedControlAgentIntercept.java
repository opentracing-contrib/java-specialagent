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

import org.mule.runtime.module.artifact.api.classloader.FineGrainedControlClassLoader;
import org.mule.runtime.module.artifact.api.classloader.LookupStrategy;

public class FineGrainedControlAgentIntercept {
    private static final String CLS_SFX = ".class";

    public static void exit(Object thiz, Object returned, Object resObj) {
        String resName = (String) resObj;

        // if it's a class that wasn't found then we continue
        if (returned != null || !resName.endsWith(CLS_SFX))
            return;

        String clazzName = resName.substring(0, resName.length() - CLS_SFX.length()).replace('/', '.');

        // See: https://github.com/mulesoft/mule/blob/mule-4.2.2/modules/artifact/src/main/java/org/mule/runtime/module/artifact/api/classloader/FineGrainedControlClassLoader.java#L67
        FineGrainedControlClassLoader loader = (FineGrainedControlClassLoader) thiz;
        LookupStrategy lookupStrategy = loader.getClassLoaderLookupPolicy().getClassLookupStrategy(clazzName);

        if (lookupStrategy == null)
            return;

        for (ClassLoader classLoader : lookupStrategy.getClassLoaders(loader)) {
            if (classLoader != null && classLoader != thiz) {
                returned = classLoader.getResource(resName);
            } else {
                returned = ClassLoader.getSystemResource(resName);
            }

            if (returned != null)
                return;
        }
    }
}
