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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name="verify", defaultPhase=LifecyclePhase.VERIFY, requiresDependencyResolution=ResolutionScope.TEST)
@Execute(goal="verify")
public final class VerifyMojo extends AbstractMojo {
  @Parameter(defaultValue="${project}", required=true, readonly=true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    final File dir = new File(project.getBuild().getOutputDirectory(), "META-INF/opentracing-specialagent");
    if (!dir.exists())
      return;

    try {
      for (final File file : dir.listFiles()) {
        if (!file.getName().endsWith(".jar"))
          throw new IllegalStateException("Unexpected file: " + file.getAbsolutePath());

        boolean hasOtaRulesMf = false;
        boolean hasFingerprintBin = false;
        boolean hasDependenciesTgf = false;
        boolean hasTestManifest = false;
        try (final JarFile jarFile = new JarFile(file)) {
          final Enumeration<JarEntry> entries = jarFile.entries();
          while (entries.hasMoreElements()) {
            final String entry = entries.nextElement().getName();
            if (ByteBuddyManager.RULES_FILE.equals(entry))
              hasOtaRulesMf = true;
            else if ("fingerprint.bin".equals(entry))
              hasFingerprintBin = true;
            else if ("dependencies.tgf".equals(entry))
              hasDependenciesTgf = true;
            else if ("META-INF/opentracing-specialagent/TEST-MANIFEST.MF".equals(entry))
              hasTestManifest = true;
          }
        }

        if (!hasOtaRulesMf)
          continue;

        if (!hasFingerprintBin)
          throw new MojoExecutionException(file.getName() + " does not have fingerprint.bin");

        if (!hasDependenciesTgf)
          throw new MojoExecutionException(file.getName() + " does not have dependencies.tgf");

        if (!hasTestManifest)
          throw new MojoExecutionException(file.getName() + " does not have AgentRunner tests");
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}