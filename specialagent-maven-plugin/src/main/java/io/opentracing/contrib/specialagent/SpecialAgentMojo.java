/* Copyright 2018 The OpenTracing Authors
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.repository.ComponentDependency;

import io.opentracing.contrib.specialagent.ReferralScanner.Manifest;

/**
 * Mojo that fingerprints 3rd-party library bytecode to ensure compatibility of
 * instrumentation plugins. The implementation uses introspection to record the
 * following information from JARs in 3rd-party libraries:
 * <ol>
 * <li>Each class name in each package in each JAR of the 3rd-party
 * library.</li>
 * <li>The superclass and interfaces of each class (to get the class
 * hierarchy).</li>
 * <li>The public-, protected-, and package-level visibility methods (their
 * names, return types, and parameter signatures -- class names of the
 * parameters).</li>
 * <li>The public-, protected-, and package-level visibility fields (their names
 * and class types).</li>
 * </ol>
 */
@Mojo(name="fingerprint", defaultPhase=LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution=ResolutionScope.TEST)
@Execute(goal="fingerprint")
public final class SpecialAgentMojo extends AbstractMojo {
  /**
   * Returns an {@code Artifact} representation of {@code dependency}, qualified
   * by {@code artifactHandler}.
   *
   * @param dependency The {@code ComponentDependency}.
   * @param artifactHandler The {@code ArtifactHandler}.
   * @return A {@code Artifact} representation of {@code dependency}, qualified
   *         by {@code artifactHandler}.
   * @throws NullPointerException If {@code dependency} or
   *           {@code artifactHandler} is null.
   */
  public static Artifact toArtifact(final ComponentDependency dependency, final ArtifactHandler artifactHandler) {
    return new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), null, dependency.getType(), null, artifactHandler);
  }

  /**
   * Returns the filesystem path of {@code artifact} located in
   * {@code localRepository}.
   *
   * @param localRepository The local repository reference.
   * @param artifact The artifact.
   * @return The filesystem path of {@code dependency} located in
   *         {@code localRepository}.
   * @throws NullPointerException If {@code localRepository} or {@code artifact}
   *           is null.
   */
  public static URL getPathOf(final ArtifactRepository localRepository, final Artifact artifact) {
    final StringBuilder builder = new StringBuilder();
    builder.append(localRepository.getBasedir());
    builder.append(File.separatorChar);
    builder.append(artifact.getGroupId().replace('.', File.separatorChar));
    builder.append(File.separatorChar);
    builder.append(artifact.getArtifactId());
    builder.append(File.separatorChar);
    builder.append(artifact.getVersion());
    builder.append(File.separatorChar);
    builder.append(artifact.getArtifactId());
    builder.append('-');
    builder.append(artifact.getVersion());
    if ("test-jar".equals(artifact.getType()))
      builder.append("-tests");

    try {
      return new URL("file", "", builder.append(".jar").toString());
    }
    catch (final MalformedURLException e) {
      throw new UnsupportedOperationException(e);
    }
  }

  /**
   * Returns a list of {@code URL} objects representing paths of
   * {@link Artifact} objects marked with {@code <optional>true</optional>} in
   * the specified iterator.
   *
   * @param localRepository The local {@link ArtifactRepository}.
   * @param iterator The {@code Iterator} of {@code Artifact} objects.
   * @param depth The depth value for stack tracking (must be called with 0).
   * @return A list of dependency paths in the specified {@code iterator}.
   */
  private static URL[] getDependencyPaths(final ArtifactRepository localRepository, final boolean optional, final Iterator<Artifact> iterator, final int depth) {
    while (iterator.hasNext()) {
      final Artifact dependency = iterator.next();
      if (optional == dependency.isOptional()) {
        final URL url = getPathOf(localRepository, dependency);
        final URL[] urls = getDependencyPaths(localRepository, optional, iterator, depth + 1);
        if (urls != null && url != null)
          urls[depth] = url;

        return urls;
      }
    }

    return depth == 0 ? null : new URL[depth];
  }

  @Parameter(property="destFile", required=true)
  private File destFile;

  @Parameter(defaultValue="${project}", required=true, readonly=true)
  protected MavenProject project;

  @Parameter(defaultValue="${localRepository}")
  private ArtifactRepository localRepository;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      final URL[] optionalDeps = getDependencyPaths(localRepository, true, project.getArtifacts().iterator(), 0);
      if (optionalDeps == null) {
        getLog().warn("No dependencies were found with <optional>true</optional> -- " + PluginClassLoader.FINGERPRINT_FILE + " will not be generated");
        return;
      }

      final URL[] nonOptionalDeps = getDependencyPaths(localRepository, false, project.getArtifacts().iterator(), 0);

      final Manifest referrals = new Manifest();
      final ReferralScanner scanner = new ReferralScanner(referrals);
      scanner.scanReferrals(nonOptionalDeps);

      final LibraryFingerprint libraryDigest = new LibraryFingerprint(new URLClassLoader(nonOptionalDeps), referrals, optionalDeps);
      destFile.getParentFile().mkdirs();
      System.err.println(Arrays.toString(optionalDeps));
      libraryDigest.toFile(destFile);
    }
    catch (final IOException e) {
      throw new MojoFailureException(null, e);
    }
  }
}