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
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.resolvers.ResolveDependenciesMojo;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;

@Mojo(name = "test-compatibility", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(goal = "test-compatibility")
public final class CompatibilityTestMojo extends ResolveDependenciesMojo {
  private static final HashMap<String,List<Dependency>> artifactToDependencies = new HashMap<>();

  private boolean downloadVersions(final Repository repository, final String groupId, final String artifactId, final SortedSet<String> versions) throws MojoExecutionException {
    try {
      final URL url = new URL(repository.getUrl() + (repository.getUrl().endsWith("/") ? "" : "/") + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml");
      String data;
      try (final InputStream in = url.openStream()) {
        data = new String(AssembleUtil.readBytes(in));
      }

      getLog().info("Retrieving available versions for: " + groupId + ":" + artifactId);

      int start = data.indexOf("<versioning>");
      if (start == -1)
        return false;

      int end = data.indexOf("</versioning>");
      data = data.substring(start + 12, end);
      start = end = 0;

      for (; start != -1; end += 10) {
        start = data.indexOf("<version>", end);
        if (start == -1)
          return true;

        end = data.indexOf("</version>", start + 9);
        if (end == -1)
          return true;

        versions.add(data.substring(start + 9, end).trim());
      }

      return true;
    }
    catch (final MalformedURLException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    catch (final IOException e) {
      return false;
    }
  }

  private List<Dependency> downloadVersions(final String groupId, final String artifactId) throws MojoExecutionException {
    final TreeSet<String> versions = new TreeSet<>();
    final List<Repository> repositories = getProject().getRepositories();
    for (final Repository repository : repositories)
      downloadVersions(repository, groupId, artifactId, versions);

    if (versions.size() == 0)
      throw new MojoExecutionException("Artifact " + groupId + ":" + artifactId + " was not found in any repositories:\n" + AssembleUtil.toIndentedString(repositories));

    final List<Dependency> dependencies = new ArrayList<>();
    for (final String version : versions)
      dependencies.add(MavenUtil.newDependency(groupId, artifactId, version));

    return dependencies;
  }

  private List<Dependency> getVersions(final String groupId, final String artifactId) throws MojoExecutionException {
    final String key = groupId + ":" + artifactId;
    if (artifactToDependencies.containsKey(key))
      return artifactToDependencies.get(key);

    final List<Dependency> versions = downloadVersions(groupId, artifactId);
    artifactToDependencies.put(key, versions);
    return versions;
  }

  @Inject
  private ProjectDependenciesResolver projectDependenciesResolver;

  @Parameter(defaultValue="${localRepository}", required=true, readonly=true)
  private ArtifactRepository localRepository;

  @Parameter(property = "failAtEnd")
  private boolean failAtEnd;

  @Parameter
  private String passCompatibility;

  @Parameter
  private String failCompatibility;

  private final List<URL> classpath = new ArrayList<>();

  private LibraryFingerprint fingerprint = null;

  private LibraryFingerprint getFingerprint() throws IOException {
    return fingerprint == null ? fingerprint = LibraryFingerprint.fromFile(new File(getProject().getBuild().getOutputDirectory(), UtilConstants.FINGERPRINT_FILE).toURI().toURL()) : fingerprint;
  }

  private static Set<Artifact> updateArtifactVersion(final Set<Artifact> artifacts, final Dependency dependency) {
    boolean changed = false;
    for (final Artifact artifact : artifacts) {
      if (artifact.isOptional()) {
        if (dependency.getGroupId().equals(artifact.getGroupId()) && dependency.getArtifactId().equals(artifact.getArtifactId())) {
          changed = true;
          artifact.setVersion(dependency.getVersion());
          break;
        }
      }
    }

    if (!changed)
      throw new RuntimeException("Compatibility spec caused no changes to dependency set");

    return artifacts;
  }

  @Override
  public String getOutput(final boolean outputAbsoluteArtifactFilename, final boolean theOutputScope, final boolean theSort) {
    return "";
  }

  @Override
  protected DependencyStatusSets getDependencySets(final boolean stopOnFailure, final boolean includeParents) throws MojoExecutionException {
    final DependencyStatusSets sets = super.getDependencySets(stopOnFailure, includeParents);
    for (final Artifact artifact : sets.getResolvedDependencies())
      classpath.add(MavenUtil.getPathOf(localRepository, artifact));

    return sets;
  }

  private Object getRepositorySystemSession() {
    try {
      final Field field = session.getClass().getDeclaredField("repositorySession");
      field.setAccessible(true);
      return field.get(session);
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private DefaultDependencyResolutionRequest newDefaultDependencyResolutionRequest(final MavenProject project) {
    try {
      for (final Constructor<?> constructor : DefaultDependencyResolutionRequest.class.getConstructors())
        if (constructor.getParameterTypes().length == 2 && constructor.getParameterTypes()[0] == MavenProject.class)
          return (DefaultDependencyResolutionRequest)constructor.newInstance(project, getRepositorySystemSession());

      return null;
    }
    catch (final IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static String print(final Dependency dependency) {
    return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
  }

  private boolean resolveDependencies(final MavenProject project) throws DependencyResolutionException {
    try {
      projectDependenciesResolver.resolve(newDefaultDependencyResolutionRequest(project));
      return true;
    }
    catch (final DependencyResolutionException e) {
//      if (!(e.getCause() instanceof DependencyCollectionException) || !(e.getCause().getCause() instanceof ArtifactDescriptorException) || !(e.getCause().getCause().getCause() instanceof ArtifactResolutionException))
//        throw e;
//
//      final String message = e.getCause().getCause().getCause().getMessage();
//      final int index = message.indexOf("Authorization failed for ");
//      if (index == -1)
//        throw e;
//
//      if (!message.endsWith(" 403 Forbidden"))
//        throw new IllegalStateException("Expected exception message to end with \" 403 Forbidden\": " + message);
//
//      getLog().warn(message.substring(index));
      getLog().warn(e.getMessage(), e);
      return false;
    }
  }

  private void resetProjectDependencies(final Dependency dependency) {
    classpath.clear();
    updateArtifactVersion(getProject().getArtifacts(), dependency);
    updateArtifactVersion(getProject().getDependencyArtifacts(), dependency);
  }

  private List<String> errors = new ArrayList<>();

  private void assertCompatibility(final Dependency dependency, final boolean shouldPass) throws DependencyResolutionException, IOException, MojoExecutionException {
    resetProjectDependencies(dependency);
    getLog().info("|-- " + print(dependency));
    resolveDependencies(getProject());
    super.doExecute();
    try (final URLClassLoader classLoader = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), null)) {
      final List<FingerprintError> errors = getFingerprint().isCompatible(classLoader);
      if (errors == null != shouldPass) {
        final String error = print(dependency) + " should have " + (errors == null ? "failed" : "passed:\n" + AssembleUtil.toIndentedString(errors));
        if (failAtEnd)
          this.errors.add(error);
        else
          throw new MojoExecutionException(error + "\nClasspath:\n" + AssembleUtil.toIndentedString(classpath));
      }
    }
  }

  private void assertCompatibility(final String compatibilityProperty, final boolean shouldPass) throws DependencyResolutionException, IOException, MojoExecutionException, InvalidVersionSpecificationException {
    final String[] parts = compatibilityProperty.split(":");
    final String groupId = parts[0];
    final String artifactId = parts[1];
    final VersionRange versionRange = VersionRange.createFromVersionSpec(parts[2]);
    final List<Dependency> dependencies = getVersions(groupId, artifactId);
    getLog().info("Running " + (shouldPass ? "PASS" : "FAIL") + " compatibility tests...");
    getLog().info(">>> " + compatibilityProperty);
    for (final Dependency dependency : dependencies)
      if (versionRange.containsVersion(new DefaultArtifactVersion(dependency.getVersion())))
        assertCompatibility(dependency, shouldPass);
  }

  private List<Dependency> getFingerprintedDependencies() {
    final List<Dependency> dependencies = new ArrayList<>();
    for (final Dependency dependency : getProject().getDependencies())
      if (dependency.isOptional())
        dependencies.add(dependency);

    return dependencies;
  }

  @Override
  protected void doExecute() throws MojoExecutionException {
    if ("pom".equalsIgnoreCase(getProject().getPackaging())) {
      getLog().info("Skipping for \"pom\" module.");
      return;
    }

    try {
      boolean wasRun = false;
      if (passCompatibility != null && (wasRun = true))
        assertCompatibility(passCompatibility, true);

      if (failCompatibility != null && (wasRun = true))
        assertCompatibility(failCompatibility, false);

      if (failAtEnd && errors.size() > 0)
        throw new MojoExecutionException("Failed compatibility tests:\n" + AssembleUtil.toIndentedString(errors));

      if (wasRun)
        return;

      final List<Dependency> fingerprintedDependencies = getFingerprintedDependencies();
      if (fingerprintedDependencies.size() > 0)
        throw new MojoExecutionException("No compatibility tests were run for verions of:\n" + AssembleUtil.toIndentedString(fingerprintedDependencies));
    }
    catch (final DependencyResolutionException | IOException | InvalidVersionSpecificationException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }
}