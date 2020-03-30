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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public final class MavenUtil {
  private static final Set<String> jarTypes = new HashSet<>(Arrays.asList("jar", "test-jar", "maven-plugin", "ejb", "ejb-client", "java-source", "javadoc"));
  private static final String[] scopes = {"compile", "provided", "runtime", "system", "test", "isolated"};

  /**
   * Tests if the specified string is a name of a Maven scope.
   *
   * @param scope The string to test.
   * @return {@code true} if the specified string is a name of a Maven scope.
   */
  private static boolean isScope(final String scope) {
    for (int i = 0; i < scopes.length; ++i)
      if (scopes[i].equals(scope))
        return true;

    return false;
  }

  private static boolean contains(final Object[] array, final Object obj) {
    for (int i = 0; i < array.length; ++i)
      if (obj == null ? array[i] == null : obj.equals(array[i]))
        return true;

    return false;
  }

  public static MavenDependency getDependency(final String dependency, final String ... scopes) {
    final int c0 = dependency.indexOf(':');
    final String groupId = dependency.substring(0, c0);

    // artifactId
    final int c1 = dependency.indexOf(':', c0 + 1);
    final String artifactId = dependency.substring(c0 + 1, c1);

    // type
    final int c2 = dependency.indexOf(':', c1 + 1);
    final String type = dependency.substring(c1 + 1, c2);
//    final String ext = getExtension(type);

    // classifier or version
    final int c3 = dependency.indexOf(':', c2 + 1);
    final String classifierOrVersion = dependency.substring(c2 + 1, c3 > c2 ? c3 : dependency.length());

    // version or scope
    final int c4 = c3 == -1 ? -1 : dependency.indexOf(':', c3 + 1);
    final String versionOrScope = c3 == -1 ? null : dependency.substring(c3 + 1, c4 > c3 ? c4 : dependency.length());

    final String scope = c4 == -1 ? null : dependency.substring(c4 + 1);

    if (scope != null) {
      if (scopes != null && !contains(scopes, scope))
        return null;

      return new MavenDependency(groupId, artifactId, versionOrScope, classifierOrVersion, type);
    }
    else if (versionOrScope != null) {
      final boolean isScope = isScope(versionOrScope);
      if (scopes != null && (isScope ? !contains(scopes, versionOrScope) : !contains(scopes, "compile")))
        return null;

      if (isScope)
        return new MavenDependency(groupId, artifactId, classifierOrVersion, null, type);

      return new MavenDependency(groupId, artifactId, versionOrScope, classifierOrVersion, type);
    }
    else {
      if (scopes != null && !contains(scopes, "compile"))
        return null;

      return new MavenDependency(groupId, artifactId, classifierOrVersion, null, type);
    }
  }

  /**
   * Selects the resource names from the specified TGF-formatted string of Maven
   * dependencies {@code tgf} that match the specification of the following
   * parameters.
   *
   * @param tgf The TGF-formatted string of Maven dependencies.
   * @param isOptional Whether the dependency is marked as {@code (optional)}.
   * @param scopes An array of Maven scopes to include in the returned set, or
   *          {@code null} to include all scopes.
   * @return A {@code Set} of resource names that match the call parameters.
   * @throws IOException If an I/O error has occurred.
   */
  public static Set<File> selectFromTgf(final String tgf, final boolean isOptional, final String ... scopes) throws IOException {
    final Set<MavenDependency> dependencies = selectDependenciesFromTgf(tgf, isOptional, scopes);
    final Set<File> files = new LinkedHashSet<>();
    for (final MavenDependency dependency : dependencies) {
      final File file = new File(MavenUtil.getPathOf(null, dependency));
      files.add(file);
    }

    return files;
  }

  public static Set<MavenDependency> selectDependenciesFromTgf(final String tgf, final boolean isOptional, final String ... scopes) {
    final Set<MavenDependency> dependencies = new LinkedHashSet<>();
    final StringTokenizer tokenizer = new StringTokenizer(tgf, "\r\n");
    final boolean includesCompileScope = contains(scopes, "compile");
    for (int i = 0; tokenizer.hasMoreTokens(); ++i) {
      String token = tokenizer.nextToken().trim();
      // Special case: include the artifact itself if (isOptional=true, and scope=compile)
      final boolean matchOptionalCompile = i == 0 && isOptional && includesCompileScope;
      if (i == 0 && !matchOptionalCompile)
        continue;

      if ("#".equals(token))
        break;

      final boolean optional = token.endsWith(" (optional)");
      if (optional) {
        if (!isOptional)
          continue;

        token = token.substring(0, token.length() - 11);
      }
      else if (isOptional && !matchOptionalCompile) {
        continue;
      }

      // groupId
      final int firstSpace = token.indexOf(' ');
      int lastSpace = token.indexOf(' ', firstSpace + 1);
      if (lastSpace == -1)
        lastSpace = token.length();

      final MavenDependency dependency = getDependency(token.substring(firstSpace + 1, lastSpace), scopes);
      if (dependency != null)
        dependencies.add(dependency);
    }

    return dependencies;
  }

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

  public static String lookupVersion(final MavenProject project, final MavenDependency mavenDependency) throws MojoExecutionException {
    final DependencyManagement dependencyManagement = project.getModel().getDependencyManagement();
    if (dependencyManagement != null)
      for (final Dependency dependency : dependencyManagement.getDependencies())
        if (dependency.getGroupId().equals(mavenDependency.getGroupId()) && dependency.getArtifactId().equals(mavenDependency.getArtifactId()))
          return dependency.getVersion();

    if (project.getParent() == null)
      throw new MojoExecutionException("Was not able to find the version of: " + mavenDependency.getGroupId() + ":" + mavenDependency.getArtifactId());

    return lookupVersion(project.getParent(), mavenDependency);
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

  public static String getPathOf(final String localRepositoryPath, final MavenDependency dependency) {
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

  private static Model getModel(final File pomFile) throws IOException, XmlPullParserException {
    final MavenXpp3Reader reader = new MavenXpp3Reader();
    return reader.read(new FileReader(pomFile));
  }

  public static String getArtifactVersion(final File dir) {
    try {
      final Model model = getModel(new File(dir, "pom.xml"));
      return model.getVersion() != null ? model.getVersion() : model.getParent().getVersion();
    }
    catch (final IOException | XmlPullParserException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String getArtifactFile(final File dir) {
    try {
      final Model model = getModel(new File(dir, "pom.xml"));
      final String version = model.getVersion() != null ? model.getVersion() : model.getParent().getVersion();
      return model.getArtifactId() + "-" + version + ".jar";
    }
    catch (final IOException | XmlPullParserException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Filters the specified array of {@code File} objects by checking if the file
   * name is included in the specified set of files to match.
   *
   * @param files The array of {@code File} objects to filter.
   * @param matches The set of {@code File} objects whose names are to be
   *          matched by the specified array of URL objects.
   * @param index The index value for stack tracking (must be called with 0).
   * @param depth The depth value for stack tracking (must be called with 0).
   * @return An array of {@code File} objects that have file names that belong
   *         to the specified files to match.
   */
  private static File[] filterUrlFileNames(final File[] files, final Set<File> matches, final int index, final int depth) {
    for (int i = index; i < files.length; ++i) {
      final File file = files[i];
      final String artifact;
      if (file.isDirectory() && "target".equals(file.getParentFile().getName()) && "classes".equals(file.getName()))
        artifact = getArtifactFile(file.getParentFile().getParentFile());
      else if (file.isFile() && file.getName().endsWith(".jar"))
        artifact = file.getName();
      else
        continue;

      for (final File match : matches) {
        if (artifact.equals(match.getName())) {
          final File[] results = filterUrlFileNames(files, matches, i + 1, depth + 1);
          results[depth] = file;
          return results;
        }
      }
    }

    return depth == 0 ? null : new File[depth];
  }

  /**
   * Filter the specified array of {@code File} objects to return the
   * Instrumentation Rule files as specified by the Dependency TGF file at
   * {@code dependencyUrl}.
   *
   * @param files The array of {@code File} objects to filter.
   * @param dependenciesTgf The contents of the TGF file that specify the
   *          dependencies.
   * @param includeOptional Whether to include dependencies marked as
   *          {@code (optional)}.
   * @param scopes An array of Maven scopes to include in the returned set, or
   *          {@code null} to include all scopes.
   * @return An array of {@code File} objects representing Instrumentation Rule
   *         files.
   * @throws IOException If an I/O error has occurred.
   */
  public static File[] filterRuleURLs(final File[] files, final String dependenciesTgf, final boolean includeOptional, final String ... scopes) throws IOException {
    final Set<File> matches = MavenUtil.selectFromTgf(dependenciesTgf, includeOptional, scopes);
    return filterUrlFileNames(files, matches, 0, 0);
  }

  private MavenUtil() {
  }
}