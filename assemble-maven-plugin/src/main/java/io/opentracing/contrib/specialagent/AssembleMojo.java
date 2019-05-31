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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
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
  private static final String outPath = "dependencies/META-INF/opentracing-specialagent";
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

  @Override
  protected void doExecute() throws MojoExecutionException {
    this.includeScope = declarationScopeOfInstrumentationPlugins;
    try {
      final File destPath = new File(getProject().getBuild().getDirectory(), outPath);
      destPath.mkdirs();

      final DependencyStatusSets dependencies = getDependencySets(false, false);
      final Set<Artifact> artifacts = dependencies.getResolvedDependencies();
      for (final Artifact artifact : artifacts) {
        File jarFile = AssembleUtil.getFileForDependency(artifact.toString(), declarationScopeOfInstrumentationPlugins);
        if (jarFile != null) {
          jarFile = new File(localRepository.getBasedir(), jarFile.getPath());
          final String dependenciesTgf = AssembleUtil.readFileFromJar(jarFile, "dependencies.tgf");
          if (dependenciesTgf != null) {
            copyDependencies(dependenciesTgf, destPath);
          }
          else if (AssembleUtil.hasFileInJar(jarFile, "META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory")) {
            Files.copy(jarFile.toPath(), new File(destPath, jarFile.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    }
    catch (final IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }
}