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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.lifecycle.internal.ProjectIndex;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.project.artifact.ProjectArtifactsCache;

@Mojo(name = "test-compatibility", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(goal = "test-compatibility")
public final class CompatibilityTestMojo extends AbstractMojo {
  private static final HashMap<String,List<Dependency>> artifactToDependencies = new HashMap<>();

  @Inject
  private MojoExecutor mojoExecutor;

  @Inject
  private ProjectDependenciesResolver projectDependenciesResolver;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Parameter(defaultValue="${mojoExecution}", required=true, readonly=true)
  protected MojoExecution execution;

  @Parameter(defaultValue="${localRepository}", required=true, readonly=true)
  private ArtifactRepository localRepository;

  @Parameter(property = "failAtEnd")
  private boolean failAtEnd;

  @Parameter
  private String passCompatibility;

  @Parameter
  private String failCompatibility;

  private LibraryFingerprint fingerprint = null;

  private LibraryFingerprint getFingerprint() throws IOException {
    return fingerprint == null ? fingerprint = LibraryFingerprint.fromFile(new File(project.getBuild().getOutputDirectory(), UtilConstants.FINGERPRINT_FILE).toURI().toURL()) : fingerprint;
  }

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
    final List<Repository> repositories = project.getRepositories();
    for (final Repository repository : repositories)
      downloadVersions(repository, groupId, artifactId, versions);

    if (versions.size() == 0)
      throw new MojoExecutionException("Artifact " + groupId + ":" + artifactId + " was not found in any repositories:\n" + AssembleUtil.toIndentedString(repositories));

    final List<Dependency> dependencies = new ArrayList<>();
    for (final String version : versions)
      dependencies.add(MavenUtil.newDependency(groupId, artifactId, version));

    return dependencies;
  }

  private void getVersions(final String groupId, final String artifactId, final List<Dependency> result, final VersionRange versionRange) throws MojoExecutionException {
    final String key = groupId + ":" + artifactId;
    final List<Dependency> dependencies;
    if (artifactToDependencies.containsKey(key))
      dependencies = artifactToDependencies.get(key);
    else
      artifactToDependencies.put(key, dependencies = downloadVersions(groupId, artifactId));

    for (final Dependency dependency : dependencies)
      if (versionRange.containsVersion(new DefaultArtifactVersion(dependency.getVersion())))
        result.add(dependency);
  }

  private static void rollbackArtifactVersion(final List<Dependency> artifacts, final List<Dependency> rollbacks) {
    artifacts.clear();
    for (final Dependency artifact : rollbacks)
      artifacts.add(artifact);
  }

  private static List<Dependency> updateArtifactVersion(final List<Dependency> artifacts, final Dependency dependency) {
    final Set<Dependency> used = new HashSet<>();
    final List<Dependency> rollback = new ArrayList<>();
    final List<Dependency> clones = new ArrayList<>();
    for (final Dependency artifact : artifacts) {
      rollback.add(artifact);
      if (artifact.isOptional() && !used.contains(dependency) && dependency.getGroupId().equals(artifact.getGroupId()) && dependency.getArtifactId().equals(artifact.getArtifactId())) {
        final Dependency clone = artifact.clone();
        clone.setVersion(dependency.getVersion());
        clones.add(clone);
        used.add(dependency);
      }
      else {
        clones.add(artifact);
      }
    }

    if (!used.contains(dependency))
      clones.add(0, MavenUtil.newDependency(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion()));

    artifacts.clear();
    for (final Dependency artifact : clones)
      artifacts.add(artifact);

    return rollback;
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

  private void resolveDependencies(final MavenProject project) throws DependencyResolutionException, LifecycleExecutionException {
    final Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();
    final Map<String,List<MojoExecution>> executions = new LinkedHashMap<>(execution.getForkedExecutions());
    final ExecutionListener executionListener = session.getRequest().getExecutionListener();
    try {
      project.setDependencyArtifacts(null);
      execution.getForkedExecutions().clear();
      session.getRequest().setExecutionListener(null);
      mojoExecutor.execute(session, Collections.singletonList(execution), new ProjectIndex(session.getProjects()));
    }
    finally {
      execution.getForkedExecutions().putAll(executions);
      session.getRequest().setExecutionListener(executionListener);
      project.setDependencyArtifacts(dependencyArtifacts);
    }

    projectDependenciesResolver.resolve(newDefaultDependencyResolutionRequest(project));
  }

  @Inject
  private ProjectArtifactsCache projectArtifactsCache;

  private List<String> errors = new ArrayList<>();

  private void assertCompatibility(final Dependency dependency, final boolean shouldPass) throws DependencyResolutionException, IOException, LifecycleExecutionException, MojoExecutionException {
    projectArtifactsCache.flush();
    final List<Dependency> rollback1 = updateArtifactVersion(project.getDependencies(), dependency);

    getLog().info("|-- " + print(dependency));
    resolveDependencies(project);
    final List<URL> classpath = new ArrayList<>();
    for (final Artifact artifact : project.getArtifacts())
      classpath.add(MavenUtil.getPathOf(localRepository, artifact));

//    System.err.println(classpath);
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

    rollbackArtifactVersion(project.getDependencies(), rollback1);
  }

  private void assertCompatibility(final String compatibilityProperty, final boolean shouldPass) throws DependencyResolutionException, IOException, LifecycleExecutionException, MojoExecutionException, InvalidVersionSpecificationException {
    getLog().info("Running " + (shouldPass ? "PASS" : "FAIL") + " compatibility tests...");
    getLog().info(">>> " + compatibilityProperty);
    final List<Dependency> dependencies = new ArrayList<>();
    final String[] compatibilitySpecs = compatibilityProperty.split(";");
    for (final String compatibilitySpec : compatibilitySpecs) {
      final String[] parts = compatibilitySpec.split(":");
      final String groupId = parts[0];
      final String artifactId = parts[1];
      final VersionRange versionRange = VersionRange.createFromVersionSpec(parts[2]);
      getVersions(groupId, artifactId, dependencies, versionRange);
    }

    for (final Dependency dependency : dependencies)
      assertCompatibility(dependency, shouldPass);
  }

  private List<Dependency> getFingerprintedDependencies() {
    final List<Dependency> dependencies = new ArrayList<>();
    for (final Dependency dependency : project.getDependencies())
      if (dependency.isOptional())
        dependencies.add(dependency);

    return dependencies;
  }

  private static boolean isRunning;

  @Override
  public void execute() throws MojoExecutionException {
    if (isRunning)
      return;

    isRunning = true;
    if ("pom".equalsIgnoreCase(project.getPackaging())) {
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

      if (wasRun) {
        resolveDependencies(project);
        return;
      }

      final List<Dependency> fingerprintedDependencies = getFingerprintedDependencies();
      if (fingerprintedDependencies.size() > 0)
        throw new MojoExecutionException("No compatibility tests were run for verions of:\n" + AssembleUtil.toIndentedString(fingerprintedDependencies));
    }
    catch (final DependencyResolutionException | IOException | InvalidVersionSpecificationException | LifecycleExecutionException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    finally {
      isRunning = false;
    }
  }
}