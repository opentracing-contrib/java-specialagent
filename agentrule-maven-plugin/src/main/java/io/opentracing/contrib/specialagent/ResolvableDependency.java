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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public class ResolvableDependency {
  private static final HashMap<DependencyId,SortedSet<DefaultArtifactVersion>> dependencyIdToVersions = new HashMap<>();

  private static SortedSet<DefaultArtifactVersion> downloadVersions(final Log log, final DependencyId dependencyId) throws MojoExecutionException {
    try {
      final URL url = new URL(dependencyId.getRepository().getUrl() + (dependencyId.getRepository().getUrl().endsWith("/") ? "" : "/") + dependencyId.getGroupId().replace('.', '/') + "/" + dependencyId.getArtifactId() + "/maven-metadata.xml");
      String data;
      try (final InputStream in = url.openStream()) {
        data = new String(AssembleUtil.readBytes(in));
      }

      log.info("Retrieving available versions for: " + dependencyId.getGroupId() + ":" + dependencyId.getArtifactId());

      int start = data.indexOf("<versioning>");
      if (start == -1)
        return null;

      final SortedSet<DefaultArtifactVersion> versions = new TreeSet<>();
      int end = data.indexOf("</versioning>");
      data = data.substring(start + 12, end);
      start = end = 0;
      for (; start != -1; end += 10) {
        start = data.indexOf("<version>", end);
        if (start == -1)
          return versions;

        end = data.indexOf("</version>", start + 9);
        if (end == -1)
          return versions;

        versions.add(new DefaultArtifactVersion(data.substring(start + 9, end).trim()));
      }

      return versions;
    }
    catch (final MalformedURLException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    catch (final IOException e) {
      return null;
    }
  }

  private static SortedSet<DefaultArtifactVersion> resolveVersions(final MavenProject project, final Log log, final ResolvableDependency dependency) throws MojoExecutionException {
    final List<Repository> repositories = project.getRepositories();
    final SortedSet<DefaultArtifactVersion> allVersions = new TreeSet<>();
    for (final Repository repository : repositories) {
      final DependencyId dependencyId = new DependencyId(dependency, repository);
      final SortedSet<DefaultArtifactVersion> versions;
      if (dependencyIdToVersions.containsKey(dependencyId))
        versions = dependencyIdToVersions.get(dependencyId);
      else
        dependencyIdToVersions.put(dependencyId, versions = downloadVersions(log, dependencyId));

      if (versions != null)
        allVersions.addAll(versions);
    }

    if (allVersions.size() == 0)
      throw new MojoExecutionException("Artifact " + dependency.getGroupId() + ":" + dependency.getArtifactId() + " was not found in any repositories:\n" + AssembleUtil.toIndentedString(repositories));

    return allVersions;
  }

  public static ResolvableDependency parse(final String str) {
    final String[] parts = str.split(":");
    return new ResolvableDependency(parts[0], parts[1], parts[2]);
  }

  private String groupId;
  private String artifactId;
  private String version;

  public ResolvableDependency(final String groupId, final String artifactId, final String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public ResolvableDependency() {
  }

  public String getGroupId() {
    return this.groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public String getArtifactId() {
    return this.artifactId;
  }

  public void setArtifactId(final String artifactId) {
    this.artifactId = artifactId;
  }

  public String getVersion() {
    return this.version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  private List<Dependency> resolvedVersions;

  boolean isSingleVersion() {
    return !version.contains(":") && !version.contains("[") && !version.contains("(");
  }

  boolean isOwnVersion() {
    return !version.contains(":");
  }

  List<Dependency> resolveVersions(final MavenProject project, final Log log) throws InvalidVersionSpecificationException, MojoExecutionException {
    if (resolvedVersions != null)
      return resolvedVersions;

    if (isSingleVersion())
      return resolvedVersions = Collections.singletonList(MavenUtil.newDependency(getGroupId(), getArtifactId(), version));

    final ResolvableDependency versionDependency = isOwnVersion() ? this : ResolvableDependency.parse(version);
    resolvedVersions = new ArrayList<>();

    final SortedSet<DefaultArtifactVersion> versions = resolveVersions(project, log, versionDependency);
    final VersionRange versionRange = VersionRange.createFromVersionSpec(versionDependency.getVersion());
    for (final DefaultArtifactVersion version : versions)
      if (versionRange.containsVersion(version))
        resolvedVersions.add(MavenUtil.newDependency(getGroupId(), getArtifactId(), version.toString()));

    return resolvedVersions;
  }
}