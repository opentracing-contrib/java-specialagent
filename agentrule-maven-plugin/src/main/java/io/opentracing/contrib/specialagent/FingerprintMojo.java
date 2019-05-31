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
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.tree.TreeMojo;
import org.codehaus.plexus.component.repository.ComponentDependency;

import io.opentracing.contrib.specialagent.Link.Manifest;

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
@Mojo(name="fingerprint", defaultPhase=LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution=ResolutionScope.TEST)
@Execute(goal="fingerprint")
public final class FingerprintMojo extends TreeMojo {
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
    builder.append('-').append(artifact.getVersion());
    if (artifact.getClassifier() != null)
      builder.append('-').append(artifact.getClassifier());

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
  private static URL[] getDependencyPaths(final ArtifactRepository localRepository, final String scope, final boolean optional, final Iterator<Artifact> iterator, final int depth) {
    while (iterator.hasNext()) {
      final Artifact dependency = iterator.next();
      if (optional == dependency.isOptional() && (scope == null || scope.equals(dependency.getScope()))) {
        final URL url = getPathOf(localRepository, dependency);
        final URL[] urls = getDependencyPaths(localRepository, scope, optional, iterator, depth + 1);
        if (urls != null && url != null)
          urls[depth] = url;

        return urls;
      }
    }

    return depth == 0 ? null : new URL[depth];
  }

  @Parameter(defaultValue="${localRepository}", required=true, readonly=true)
  private ArtifactRepository localRepository;

  @Parameter(defaultValue="${sa.plugin.name}", required=true, readonly=true)
  private String name;

  private void setField(final Class<? super FingerprintMojo> cls, final String fieldName, final Object value) {
    try {
      final Field field = cls.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(this, value);
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  private void createDependenciesTgf() throws MojoExecutionException, MojoFailureException {
    setField(TreeMojo.class, "outputType", "tgf");
    setField(TreeMojo.class, "outputFile", new File(getProject().getBuild().getOutputDirectory(), "dependencies.tgf"));
    super.execute();
  }

  private void createFingerprintBin() throws MojoExecutionException {
    try {
      final File destFile = new File(getProject().getBuild().getOutputDirectory(), "fingerprint.bin");
      destFile.getParentFile().mkdirs();
      final File nameFile = new File(destFile.getParentFile(), "sa.plugin.name." + name);
      if (!nameFile.exists() && !nameFile.createNewFile())
        throw new MojoExecutionException("Unable to create file: " + nameFile.getAbsolutePath());

      // The `optionalDeps` represent the 3rd-Party Library that is being instrumented
      final URL[] optionalDeps = getDependencyPaths(localRepository, null, true, getProject().getArtifacts().iterator(), 0);
      if (optionalDeps == null) {
        getLog().warn("No dependencies were found with (scope=*, optional=true), " + RuleClassLoader.FINGERPRINT_FILE + " will be empty");
        new LibraryFingerprint().toFile(destFile);
        return;
      }

      // The `compileDeps` represent the Instrumentation Plugin (this is the dependency(ies)
      // that bridges/links between the 3rd-Party Library to the Instrumentation Rule).
      final URL[] compileDeps = getDependencyPaths(localRepository, "compile", false, getProject().getArtifacts().iterator(), 1);
      // Include the compile path of the Instrumentation Rule itself, which solves the use-
      // case where there is no Instrumentation Plugin (i.e. the Instrumentation Rule directly
      // bridges/links between the 3rd-Party Library to itself).
      compileDeps[0] = new File(getProject().getBuild().getOutputDirectory()).toURI().toURL();

      final Manifest manifest = Link.createManifest(compileDeps);

      final URL[] nonOptionalDeps = getDependencyPaths(localRepository, null, false, getProject().getArtifacts().iterator(), 0);
      final LibraryFingerprint fingerprint = new LibraryFingerprint(new URLClassLoader(nonOptionalDeps), manifest, optionalDeps);
      fingerprint.toFile(destFile);
    }
    catch (final IOException e) {
      throw new MojoExecutionException(null, e);
    }
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if ("pom".equalsIgnoreCase(getProject().getPackaging()))
      return;

    if (name == null || name.length() == 0)
      throw new MojoExecutionException("The parameter 'name' is missing or invalid");

    createDependenciesTgf();
    createFingerprintBin();
  }
}