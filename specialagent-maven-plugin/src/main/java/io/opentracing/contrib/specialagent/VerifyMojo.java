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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@Mojo(name = "verify", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(goal = "verify")
public final class VerifyMojo extends AbstractMojo {
  private static final boolean ignoreMissingTestManifest = AssembleUtil.isSystemProperty("ignoreMissingTestManifest", null);

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject project;

  private Map<String,String> scanRenames() throws IOException, XmlPullParserException {
    final BufferedReader in = new BufferedReader(new FileReader(project.getParent().getFile()));
    final MavenXpp3Reader reader = new MavenXpp3Reader();
    final Model model = reader.read(in);
    final Properties properties = model.getProperties();
    final Map<String,String> renames = new HashMap<>();
    for (final Dependency dependency : model.getDependencies()) {
      final String rename = properties.getProperty(dependency.getArtifactId());
      if (rename != null)
        renames.put(dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar", rename + ".jar");
    }

    return renames;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    final File dir = new File(project.getBuild().getOutputDirectory(), UtilConstants.META_INF_PLUGIN_PATH);
    if (!dir.exists())
      return;

    try {
      final Map<String,String> renames = scanRenames();
      for (final File file : dir.listFiles()) {
        if (!file.getName().endsWith(".jar"))
          throw new IllegalStateException("Unexpected file: " + file.getAbsolutePath());

        boolean hasOtaRulesMf = false;
        boolean hasFingerprintBin = false;
        boolean hasDependenciesTgf = false;
        boolean hasTestManifest = false;
        boolean hasPluginName = false;
        try (final JarFile jarFile = new JarFile(file)) {
          final Enumeration<JarEntry> entries = jarFile.entries();
          while (entries.hasMoreElements()) {
            final String entry = entries.nextElement().getName();
            if ("otarules.mf".equals(entry)) // Deliberately unlinked from ByteBuddyManager#RULES_FILE
              hasOtaRulesMf = true;
            else if ("fingerprint.bin".equals(entry))
              hasFingerprintBin = true;
            else if ("dependencies.tgf".equals(entry))
              hasDependenciesTgf = true;
            else if (UtilConstants.META_INF_TEST_MANIFEST.equals(entry))
              hasTestManifest = true;
            else if (entry.startsWith("sa.rule.name."))
              hasPluginName = true;
          }
        }

        if (!hasOtaRulesMf) {
          final String rename = renames.get(file.getName());
          if (rename != null)
            Files.move(file.toPath(), new File(file.getParentFile(), rename).toPath(), StandardCopyOption.REPLACE_EXISTING);

          continue;
        }

        if (!hasFingerprintBin)
          throw new MojoExecutionException(file.getName() + " does not have fingerprint.bin");

        if (!hasDependenciesTgf)
          throw new MojoExecutionException(file.getName() + " does not have dependencies.tgf");

        if (!hasTestManifest) {
          final String message = file.getName() + " does not have AgentRunner tests";
          if (ignoreMissingTestManifest)
            getLog().warn(message);
          else
            throw new MojoExecutionException(message);
        }

        if (!hasPluginName)
          throw new MojoExecutionException(file.getName() + " does not have Plugin Name file");
      }
    }
    catch (final IOException | XmlPullParserException e) {
      throw new IllegalStateException(e);
    }
  }
}