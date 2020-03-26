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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class AgentRunnerClassLoader extends URLClassLoader {
  public final File[] ruleFiles;
  public final IsoClassLoader isoClassLoader;

  public AgentRunnerClassLoader(final URL[] classPath, final File[] ruleFiles, final URL[] isoUrls, final ClassLoader parent) {
    super(classPath, parent);
    this.ruleFiles = ruleFiles;
    this.isoClassLoader = new IsoClassLoader(isoUrls, this);
  }
}