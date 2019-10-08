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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.tree.TreeMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
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

  @Parameter(defaultValue="${sa.plugin.name}")
  private String name;

  @Parameter
  private List<String> presents;

  @Parameter
  private List<String> absents;

  private Object getField(final Class<? super FingerprintMojo> cls, final String fieldName) {
    try {
      final Field field = cls.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(this);
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

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

  private final DependencyNodeVisitor filterVisitor = new DependencyNodeVisitor() {
    @Override
    public boolean visit(final DependencyNode node) {
      final List<DependencyNode> children = new ArrayList<>(node.getChildren());
      final Iterator<DependencyNode> iterator = children.iterator();
      while (iterator.hasNext()) {
        final DependencyNode child = iterator.next();
        final Artifact artifact = child.getArtifact();
        if ("io.opentracing".equals(artifact.getGroupId())) {
          if (!apiVersion.equals(artifact.getVersion()))
            getLog().warn("OT API version (" + apiVersion + ") in SpecialAgent differs from OT API version (" + apiVersion + ") in plugin");

          if ("compile".equals(artifact.getScope())) {
            getLog().warn("Removing dependency provided by SpecialAgent: " + artifact);
            iterator.remove();
          }
        }
        else if ("compile".equals(artifact.getScope()) && !getProject().getDependencyArtifacts().contains(artifact)) {
          getLog().warn("Including dependency for plugin: " + artifact);
        }
      }

      if (children.size() < node.getChildren().size())
        ((DefaultDependencyNode)node).setChildren(children);

      return true;
    }

    @Override
    public boolean endVisit(final DependencyNode node) {
      return true;
    }
  };

  private void createDependenciesTgf() throws MojoExecutionException, MojoFailureException {
    getLog().info("--> dependencies.tgf <--");
    setField(TreeMojo.class, "outputType", "tgf");
    setField(TreeMojo.class, "outputFile", new File(getProject().getBuild().getOutputDirectory(), "dependencies.tgf"));
    final DependencyGraphBuilder builder = (DependencyGraphBuilder)getField(TreeMojo.class, "dependencyGraphBuilder");
    setField(TreeMojo.class, "dependencyGraphBuilder", new DependencyGraphBuilder() {
      @Override
      public DependencyNode buildDependencyGraph(final ProjectBuildingRequest buildingRequest, final ArtifactFilter filter) throws DependencyGraphBuilderException {
        final DependencyNode node = builder.buildDependencyGraph(buildingRequest, filter);
        node.accept(filterVisitor);
        return node;
      }

      @Override
      public DependencyNode buildDependencyGraph(final ProjectBuildingRequest buildingRequest, final ArtifactFilter filter, final Collection<MavenProject> reactorProjects) throws DependencyGraphBuilderException {
        final DependencyNode node = builder.buildDependencyGraph(buildingRequest, filter, reactorProjects);
        node.accept(filterVisitor);
        return node;
      }
    });
    super.execute();
  }

  private void createFingerprintBin() throws IOException {
    getLog().info("--> fingerprint.bin <--");
    final File destFile = new File(getProject().getBuild().getOutputDirectory(), UtilConstants.FINGERPRINT_FILE);
    destFile.getParentFile().mkdirs();

    // The `compileDeps` represent the Instrumentation Plugin (this is the dependency(ies)
    // that bridges/links between the 3rd-Party Library to the Instrumentation Rule).
    final URL[] compileDeps = getDependencyPaths(localRepository, "compile", false, getProject().getArtifacts().iterator(), 1);
    // Include the compile path of the Instrumentation Rule itself, which solves the use-
    // case where there is no Instrumentation Plugin (i.e. the Instrumentation Rule directly
    // bridges/links between the 3rd-Party Library to itself).
    compileDeps[0] = AssembleUtil.toURL(new File(getProject().getBuild().getOutputDirectory()));

    // The `optionalDeps` represent the 3rd-Party Library that is being instrumented
    final URL[] optionalDeps = getDependencyPaths(localRepository, null, true, getProject().getArtifacts().iterator(), 0);

    try (final URLClassLoader classLoader = new URLClassLoader(compileDeps, new URLClassLoader(optionalDeps != null ? optionalDeps : new URL[0], null))) {
      final LibraryFingerprint fingerprint = new LibraryFingerprint(classLoader, presents, absents, new MavenLogger(getLog()));
      fingerprint.toFile(destFile);
      if (getLog().isDebugEnabled())
        getLog().debug(fingerprint.toString());
    }
  }

  private void createPluginName() throws IOException, MojoExecutionException {
    if (name == null)
      name = getProject().getArtifact().getArtifactId();

    final String pluginName = "sa.plugin.name." + name;
    getLog().info("--> " + pluginName + " <--");
    final File nameFile = new File(getProject().getBuild().getOutputDirectory(), pluginName);
    if (!nameFile.exists() && !nameFile.createNewFile())
      throw new MojoExecutionException("Unable to create file: " + nameFile.getAbsolutePath());
  }

  private String apiVersion;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if ("pom".equalsIgnoreCase(getProject().getPackaging())) {
      getLog().info("Skipping for \"pom\" module.");
      return;
    }

    for (final Artifact artifact : getProject().getDependencyArtifacts()) {
      if ("io.opentracing".equals(artifact.getGroupId()) && "opentracing-api".equals(artifact.getArtifactId())) {
        apiVersion = artifact.getVersion();
        break;
      }
    }

    try {
      createDependenciesTgf();
      createFingerprintBin();
      createPluginName();
    }
    catch (final IOException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }
}