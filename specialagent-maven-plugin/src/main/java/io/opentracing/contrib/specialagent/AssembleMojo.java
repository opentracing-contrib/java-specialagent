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
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.resolvers.ResolveDependenciesMojo;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.ProjectDependenciesResolver;

@Mojo(name = "assemble", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true )
@Execute(goal = "assemble")
public final class AssembleMojo extends ResolveDependenciesMojo {
  private static final String pluginsDestDir = "dependencies/" + UtilConstants.META_INF_PLUGIN_PATH;
  private static final String isoDestDir = "dependencies/" + UtilConstants.META_INF_ISO_PATH;
  private static final String declarationScopeOfInstrumentationPlugins = "provided";

  @Inject
  private MojoExecutor executor;

  @Inject
  private ProjectDependenciesResolver projectDependenciesResolver;

  @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
  private ArtifactRepository localRepository;

  @Parameter(defaultValue = "${mojoExecution}", required = true, readonly = true)
  private MojoExecution execution;

  @Parameter
  private List<IsolatedDependency> isolatedDependencies;

  @Parameter(property = "debug")
  private boolean debug = false;

  private void copyDependencies(final String dependencyTgf, final File destPath) throws IOException {
    final Set<File> jarFiles = MavenUtil.selectFromTgf(dependencyTgf, true, "compile");
    for (final File jarFile : jarFiles) {
      final Path path = new File(localRepository.getBasedir(), jarFile.getPath()).toPath();
      Files.copy(path, new File(destPath, jarFile.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void fileCopy(final File from, final File to) throws IOException {
    if (debug)
      getLog().warn("Copying " + from.getAbsolutePath() + " to " + to.getAbsolutePath());

    Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  private static boolean isRunning;

  @Override
  protected void doExecute() throws MojoExecutionException {
    if (isRunning)
      return;

    isRunning = true;
    this.includeScope = declarationScopeOfInstrumentationPlugins;
    try {
      final File pluginsPath = new File(getProject().getBuild().getDirectory(), pluginsDestDir);
      pluginsPath.mkdirs();

      final File isoPath = new File(getProject().getBuild().getDirectory(), isoDestDir);
      isoPath.mkdirs();

      final DependencyStatusSets dependencies = getDependencySets(false, false);
      final Set<Artifact> artifacts = dependencies.getResolvedDependencies();
      if (debug)
        getLog().warn("Assembling " + artifacts.size() + " artifacts");

      for (final Artifact artifact : artifacts) {
        if (debug)
          getLog().warn("Assembling artifact: " + artifact.toString());

        final MavenDependency dependency = MavenUtil.getDependency(artifact.toString(), declarationScopeOfInstrumentationPlugins);
        if (dependency != null) {
          File jarFile = new File(MavenUtil.getPathOf(null, dependency));
          jarFile = new File(localRepository.getBasedir(), jarFile.getPath());
          String dependenciesTgf = null;
          String saRuleName = null;
          try (final ZipFile zipFile = new ZipFile(jarFile)) {
            final Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while ((dependenciesTgf == null || saRuleName == null) && enumeration.hasMoreElements()) {
              final ZipEntry entry = enumeration.nextElement();
              if (dependenciesTgf == null && "dependencies.tgf".equals(entry.getName())) {
                try (final InputStream in = zipFile.getInputStream(entry)) {
                  dependenciesTgf = new String(AssembleUtil.readBytes(in));
                }
              }
              else if (saRuleName == null && entry.getName().startsWith("sa.rule.name.")) {
                saRuleName = entry.getName().substring(13);
              }
            }
          }

          if (dependenciesTgf != null) {
            if (saRuleName == null)
              throw new MojoExecutionException("sa.rule.name file was not found");

            if (!artifact.isOptional())
              throw new MojoExecutionException("Rule " + artifact + " should be <optional>true</optional>");

            copyDependencies(dependenciesTgf, pluginsPath);
          }
          else if (debug) {
            getLog().warn("Skipping artifact [selector]: " + artifact.toString());
          }
        }
        else if (debug) {
          getLog().warn("Skipping artifact [scope mismatch]: " + artifact.toString());
        }
      }

      if (isolatedDependencies != null && isolatedDependencies.size() > 0) {
        for (final IsolatedDependency dependency : isolatedDependencies) {
          if (dependency.getVersion() != null)
            throw new MojoExecutionException("Version of " + dependency + " must be specified in <dependencyManagement>");

          dependency.setVersion(MavenUtil.lookupVersion(getProject(), dependency));
          final File jarFile = new File(MavenUtil.getPathOf(localRepository.getBasedir(), dependency));

          final String fileName;
          if (AssembleUtil.hasFileInJar(jarFile, "META-INF/services/io.opentracing.contrib.tracerresolver.TracerFactory")) {
            final String pluginName = (String)getProject().getProperties().get(dependency.getArtifactId());
            if (pluginName == null)
              throw new MojoExecutionException("Name of Tracer Plugin is missing: <properties><" + dependency.getArtifactId() + ">NAME</" + dependency.getArtifactId() + "></properties>");

            fileName = pluginName + ".jar";
          }
          else {
            fileName = jarFile.getName();
          }

          fileCopy(jarFile, new File(isoPath, fileName));
        }

        final Dependency[] rollback = MutableMojo.replaceDependencies(getProject().getDependencies(), MavenDependency.toDependencies(isolatedDependencies));
        MutableMojo.resolveDependencies(session, execution, executor, projectDependenciesResolver);
        MutableMojo.rollbackDependencies(getProject().getDependencies(), rollback);
        MutableMojo.resolveDependencies(session, execution, executor, projectDependenciesResolver);
      }
    }
    catch (final DependencyResolutionException | IOException | LifecycleExecutionException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    finally {
      isRunning = false;
    }
  }
}