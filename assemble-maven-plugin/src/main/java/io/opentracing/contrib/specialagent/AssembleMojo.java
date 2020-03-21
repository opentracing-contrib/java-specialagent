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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.resolvers.ResolveDependenciesMojo;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;

@Mojo(name = "assemble", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true )
@Execute(goal = "assemble")
public final class AssembleMojo extends ResolveDependenciesMojo {
  private static final String pluginsDestDir = "dependencies/" + UtilConstants.META_INF_PLUGIN_PATH;
  private static final String isoDestDir = "dependencies/" + UtilConstants.META_INF_ISO_PATH;
  private static final String declarationScopeOfInstrumentationPlugins = "provided";

  @Parameter(defaultValue = "${localRepository}")
  private ArtifactRepository localRepository;

  private void copyDependencies(final String dependencyTgf, final File destPath) throws IOException {
    final Set<File> jarFiles = AssembleUtil.selectFromTgf(dependencyTgf, false, new String[] {"compile"});
    for (final File jarFile : jarFiles) {
      final Path path = new File(localRepository.getBasedir(), jarFile.getPath()).toPath();
      Files.copy(path, new File(destPath, jarFile.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void fileCopy(final File from, final File to) throws IOException {
    Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  protected void doExecute() throws MojoExecutionException {
    this.includeScope = declarationScopeOfInstrumentationPlugins;
    try {
      final File pluginsPath = new File(getProject().getBuild().getDirectory(), pluginsDestDir);
      pluginsPath.mkdirs();

      final File isoPath = new File(getProject().getBuild().getDirectory(), isoDestDir);
      isoPath.mkdirs();

      final DependencyStatusSets dependencies = getDependencySets(false, false);
      final Set<Artifact> artifacts = dependencies.getResolvedDependencies();
      if (getLog().isDebugEnabled())
        getLog().debug("Assembling " + artifacts.size() + " artifacts");

      for (final Artifact artifact : artifacts) {
        if (getLog().isDebugEnabled())
          getLog().debug("Assembling artifact: " + artifact.toString());

        final Dependency dependency = AssembleUtil.getDependency(artifact.toString(), declarationScopeOfInstrumentationPlugins);
        if (dependency != null) {
          File jarFile = new File(MavenUtil.getPathOf(null, dependency));
          jarFile = new File(localRepository.getBasedir(), jarFile.getPath());
          String dependenciesTgf = null;
          try (final ZipFile zipFile = new ZipFile(jarFile)) {
            final Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
              final ZipEntry entry = enumeration.nextElement();
              if ("dependencies.tgf".equals(entry.getName())) {
                try (final InputStream in = zipFile.getInputStream(entry)) {
                  dependenciesTgf = new String(AssembleUtil.readBytes(in));
                  break;
                }
              }
            }
          }

          if (dependenciesTgf != null) {
            if (!artifact.isOptional())
              throw new MojoExecutionException("Rule " + artifact + " should be <optional>true</optional>");

            // FIXME: Add test to make sure sa.rule.name. file exists and associates an adapter
            copyDependencies(dependenciesTgf, pluginsPath);
          }
          else if (AssembleUtil.hasFileInJar(jarFile, "META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory") || AssembleUtil.hasFileInJar(jarFile, "META-INF/maven/io.opentracing.contrib/opentracing-tracerresolver/pom.xml")) {
            String pluginName = (String)getProject().getProperties().get(artifact.getArtifactId());
            if (pluginName == null) {
              getLog().warn("Name of Tracer Plugin is missing: <properties><" + artifact.getArtifactId() + ">NAME</" + artifact.getArtifactId() + "></properties>");
              pluginName = jarFile.getName();
            }
            else {
              pluginName += ".jar";
            }

            fileCopy(jarFile, new File(pluginsPath, pluginName));
          }
          else if (!artifact.isOptional()) {
            fileCopy(jarFile, new File(isoPath, jarFile.getName()));
          }
          else if (getLog().isDebugEnabled()) {
            getLog().debug("Skipping artifact [selector]: " + artifact.toString());
          }
        }
        else if (getLog().isDebugEnabled()) {
          getLog().debug("Skipping artifact [scope mismatch]: " + artifact.toString());
        }
      }
    }
    catch (final IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }
}