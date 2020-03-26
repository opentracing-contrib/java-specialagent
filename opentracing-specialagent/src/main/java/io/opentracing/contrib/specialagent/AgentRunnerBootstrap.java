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
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

import net.bytebuddy.agent.ByteBuddyAgent;

public final class AgentRunnerBootstrap {
  private static Instrumentation inst;

  private static JarFile createJarFileOfSource(final Class<?> cls) throws IOException {
    return createJarFileOfSource(new File(cls.getProtectionDomain().getCodeSource().getLocation().getPath()));
  }

  private static JarFile createJarFileOfSource(final File file) throws IOException {
    final String path = file.getAbsolutePath();
    if (file.isDirectory()) {
      if ("classes".equals(file.getName()))
        return SpecialAgentUtil.createTempJarFile(file);

      if ("test-classes".equals(file.getName()))
        return SpecialAgentUtil.createTempJarFile(new File(file.getParent(), "classes"));
    }
    else {
      if (path.endsWith(".jar"))
        return new JarFile(file);

      if (path.endsWith("-tests.jar"))
        return new JarFile(new File(path.substring(0, path.length() - 10) + ".jar"));
    }

    throw new UnsupportedOperationException("Unsupported source path: " + path);
  }

  private static JarFile[] appendSourceLocationToBootstrap(final Class<?> cls) throws IOException {
    return new JarFile[] {createJarFileOfSource(cls)};
  }

  private static JarFile[] appendSourceLocationToBootstrap(final File ... files) throws IOException {
    final JarFile[] jarFiles = new JarFile[files.length + 1];
    jarFiles[0] = createJarFileOfSource(AgentRunnerBootstrap.class);
    for (int i = 0; i < files.length; ++i)
      jarFiles[i + 1] = createJarFileOfSource(files[i]);

    return jarFiles;
  }

  static Instrumentation install(final File[] bootstrapFiles) {
    if (inst != null)
      return inst;

    try {
      final JarFile[] jarFiles = bootstrapFiles != null ? appendSourceLocationToBootstrap(bootstrapFiles) : appendSourceLocationToBootstrap(AgentRunnerBootstrap.class);
      final Instrumentation inst = ByteBuddyAgent.install();
      for (final JarFile jarFile : jarFiles)
        inst.appendToBootstrapClassLoaderSearch(jarFile);

      BootLoaderAgent.premain(inst, jarFiles);

      if (BootProxyClassLoader.INSTANCE.loadClassOrNull("io.opentracing.contrib.specialagent.Level", false) == null)
        throw new IllegalStateException();

      if (BootProxyClassLoader.INSTANCE.getResource("io/opentracing/contrib/specialagent/Adapter.class") == null)
        throw new IllegalStateException();

      return AgentRunnerBootstrap.inst = inst;
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private AgentRunnerBootstrap() {
  }
}