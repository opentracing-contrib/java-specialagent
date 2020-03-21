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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;

public final class MavenUtil {
  private static final Set<String> jarTypes = new HashSet<>(Arrays.asList("jar", "test-jar", "maven-plugin", "ejb", "ejb-client", "java-source", "javadoc"));

  /** https://maven.apache.org/ref/3.6.1/maven-core/artifact-handlers.html */
  private static String getExtension(final String type) {
    return type == null || jarTypes.contains(type) ? "jar" : type;
  }

  public static DefaultArtifact clone(final Artifact artifact) {
    final DefaultArtifact clone = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getScope(), artifact.getType(), artifact.getClassifier(), artifact.getArtifactHandler());
    clone.setAvailableVersions(artifact.getAvailableVersions());
    clone.setBaseVersion(artifact.getBaseVersion());
    clone.setDependencyFilter(artifact.getDependencyFilter());
    clone.setDependencyTrail(artifact.getDependencyTrail());
    clone.setDownloadUrl(artifact.getDownloadUrl());
    clone.setFile(artifact.getFile());
    clone.setOptional(artifact.isOptional());
    clone.setRelease(artifact.isRelease());
    clone.setRepository(artifact.getRepository());
    clone.setResolved(artifact.isResolved());
    clone.setVersionRange(artifact.getVersionRange());
    return clone;
  }

  public static Dependency newDependency(final String groupId, final String artifactId, final String version) {
    return newDependency(groupId, artifactId, version, null, null);
  }

  public static Dependency newDependency(final String groupId, final String artifactId, final String version, final String classifier, final String type) {
    final Dependency dependency = new Dependency();
    dependency.setGroupId(groupId);
    dependency.setArtifactId(artifactId);
    dependency.setVersion(version);
    dependency.setClassifier(classifier);
    if (type != null)
      dependency.setType(type);

    return dependency;
  }

  /**
   * Returns {@code true} if the specified {@link MojoExecution} is in a
   * lifecycle phase, and the name of the lifecycle phase contains "test".
   *
   * @param execution The {@link MojoExecution}.
   * @return {@code true} if the specified {@link MojoExecution} is in a
   *         lifecycle phase, and the name of the lifecycle phase contains
   *         "test".
   * @throws NullPointerException If {@code execution} is null.
   */
  public static boolean isInTestPhase(final MojoExecution execution) {
    return execution.getLifecyclePhase() != null && execution.getLifecyclePhase().contains("test");
  }

  /**
   * Returns the {@link PluginExecution} in the {@code mojoExecution}, if a
   * plugin is currently being executed.
   *
   * @param execution The {@link MojoExecution}.
   * @return The {@link PluginExecution} in the {@code mojoExecution}, if a
   *         plugin is currently being executed.
   * @throws NullPointerException If {@code execution} is null.
   */
  public static PluginExecution getPluginExecution(final MojoExecution execution) {
    final Plugin plugin = execution.getPlugin();
    plugin.flushExecutionMap();
    for (final PluginExecution pluginExecution : plugin.getExecutions())
      if (pluginExecution.getId().equals(execution.getExecutionId()))
        return pluginExecution;

    return null;
  }

  /**
   * Returns {@code true} if a calling MOJO should skip execution due to the
   * {@code -Dmaven.test.skip} property. If the {@code -Dmaven.test.skip}
   * property is present, this method will return {@code true} when the phase
   * name of MOJO or plugin {@code execution} contains the string "test".
   *
   * @param execution The {@link MojoExecution}.
   * @param mavenTestSkip The {@code -Dmaven.test.skip} property.
   * @return {@code true} if a calling MOJO should skip execution due to the
   *         {@code -Dmaven.test.skip} property.
   */
  public static boolean shouldSkip(final MojoExecution execution, final boolean mavenTestSkip) {
    if (!mavenTestSkip)
      return false;

    if (execution != null && isInTestPhase(execution))
      return true;

    final PluginExecution pluginExecution = getPluginExecution(execution);
    return pluginExecution != null && pluginExecution.getPhase() != null && pluginExecution.getPhase().contains("test");
  }

  /**
   * Returns the filesystem path of {@code artifact} located in
   * {@code localRepository}.
   *
   * @param localRepositoryPath The local repository path.
   * @param artifact The artifact.
   * @return The filesystem path of {@code dependency} located in
   *         {@code localRepository}.
   * @throws NullPointerException If {@code localRepository} or {@code artifact}
   *           is null.
   */
  public static String getPathOf(final String localRepositoryPath, final Artifact artifact) {
    return getPathOf(localRepositoryPath, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier(), artifact.getType());
  }

  public static String getPathOf(final String localRepositoryPath, final Dependency dependency) {
    return getPathOf(localRepositoryPath, dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getClassifier(), dependency.getType());
  }

  public static String getPathOf(final String localRepositoryPath, final String groupId, final String artifactId, final String version, final String classifier, final String type) {
    final StringBuilder builder = new StringBuilder();
    if (localRepositoryPath != null) {
      builder.append(localRepositoryPath);
      builder.append(File.separatorChar);
    }

    builder.append(groupId.replace('.', File.separatorChar));
    builder.append(File.separatorChar);
    builder.append(artifactId);
    builder.append(File.separatorChar);
    builder.append(version);
    builder.append(File.separatorChar);
    builder.append(artifactId);
    builder.append('-').append(version);
    if (classifier != null)
      builder.append('-').append(classifier);

    return builder.append('.').append(getExtension(type)).toString();
  }

  private MavenUtil() {
  }
}