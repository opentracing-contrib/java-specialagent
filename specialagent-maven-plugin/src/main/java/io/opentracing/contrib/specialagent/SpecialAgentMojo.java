package io.opentracing.contrib.specialagent;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
   * @param project The {@link MavenProject} for which to return the classpath.
   * @param localRepository The local {@link ArtifactRepository}.
   * @return A list of dependency paths in the specified {@link MavenProject}.
   */
  private static URL[] getOptionalDependencyPaths(final ArtifactRepository localRepository, final Iterator<Artifact> iterator, final int depth) {
    while (iterator.hasNext()) {
      final Artifact dependency = iterator.next();
      if (dependency.isOptional()) {
        final URL url = getPathOf(localRepository, dependency);
        final URL[] urls = getOptionalDependencyPaths(localRepository, iterator, depth + 1);
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
      final URL[] dependencies = getOptionalDependencyPaths(localRepository, project.getArtifacts().iterator(), 0);
      if (dependencies == null)
        throw new MojoExecutionException("No dependencies were found with <optional>true</optional>");

      final LibraryFingerprint libraryDigest = new LibraryFingerprint(dependencies);
      destFile.getParentFile().mkdirs();
      System.err.println(Arrays.toString(dependencies));
      libraryDigest.toFile(destFile);
    }
    catch (final IOException e) {
      throw new MojoFailureException(null, e);
    }
  }
}