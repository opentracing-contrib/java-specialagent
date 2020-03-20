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
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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
import org.apache.maven.plugins.dependency.tree.TGFDependencyNodeVisitor;
import org.apache.maven.plugins.dependency.tree.TreeMojo;
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
        final URL url = MavenUtil.getPathOf(localRepository, dependency);
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

  @Parameter(defaultValue="${sa.rule.name}", required=true)
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

  private class Foo implements DependencyNodeVisitor {
    private DependencyNodeVisitor target;

    private Foo(final DependencyNodeVisitor target) {
      this.target = target;
    }

    @Override
    public boolean visit(final DependencyNode node) {
      final List<DependencyNode> children = new ArrayList<>(node.getChildren());
      final Iterator<DependencyNode> iterator = children.iterator();
      while (iterator.hasNext()) {
        final DependencyNode child = iterator.next();
        final Artifact artifact = child.getArtifact();
        if ("io.opentracing".equals(artifact.getGroupId())) {
          if (apiVersion != null && !apiVersion.equals(artifact.getVersion()))
            getLog().warn("OT API version (" + apiVersion + ") in SpecialAgent differs from OT API version (" + apiVersion + ") in plugin");

          if ("compile".equals(artifact.getScope())) {
//            getLog().warn("Removing dependency provided by SpecialAgent: " + artifact);
//            iterator.remove();
          }
        }
        else if ("compile".equals(artifact.getScope()) && !getProject().getDependencyArtifacts().contains(artifact)) {
//          getLog().warn("Including dependency for plugin: " + artifact);
        }
      }

      if (children.size() < node.getChildren().size())
        ((DefaultDependencyNode)node).setChildren(children);

      final DependencyNode parent = node.getParent();
      if (parent == null || parent.getOptional() == null || !parent.getOptional() || !"provided".equals(parent.getArtifact().getScope()))
        return target.visit(node);

      return false;
    }

    @Override
    public boolean endVisit(final DependencyNode node) {
      return target.endVisit(node);
    }
  }

  @Override
  public DependencyNodeVisitor getSerializingDependencyNodeVisitor(final Writer writer) {
    final DependencyNodeVisitor visitor = super.getSerializingDependencyNodeVisitor(writer);
    if (!(visitor instanceof TGFDependencyNodeVisitor))
      return visitor;

    return new Foo(visitor);
  }

  private void createDependenciesTgf() throws MojoExecutionException, MojoFailureException {
    getLog().info("--> dependencies.tgf <--");
    setField(TreeMojo.class, "outputType", "tgf");
    setField(TreeMojo.class, "outputFile", new File(getProject().getBuild().getOutputDirectory(), "dependencies.tgf"));
    super.execute();
  }

  private void createFingerprintBin() throws IOException {
    getLog().info("--> fingerprint.bin <--");
    final File destFile = new File(getProject().getBuild().getOutputDirectory(), UtilConstants.FINGERPRINT_FILE);
    destFile.getParentFile().mkdirs();

    // The `ruleDeps` represent the Instrumentation Plugin (this is the dependency(ies)
    // that bridges/links between the 3rd-Party Library to the Instrumentation Rule).
    final URL[] ruleDeps = getDependencyPaths(localRepository, "compile", true, getProject().getArtifacts().iterator(), 1);
    // Include the compile path of the Instrumentation Rule itself, which solves the use-
    // case where there is no Instrumentation Plugin (i.e. the Instrumentation Rule directly
    // bridges/links between the 3rd-Party Library to itself).
    ruleDeps[0] = AssembleUtil.toURL(new File(getProject().getBuild().getOutputDirectory()));
    if (getLog().isDebugEnabled())
      getLog().debug("ruleDeps: " + Arrays.toString(ruleDeps));

    // The `libDeps` represent the 3rd-Party Library that is being instrumented
    final URL[] libDeps = getDependencyPaths(localRepository, "provided", true, getProject().getArtifacts().iterator(), 0);
    if (getLog().isDebugEnabled())
      getLog().debug("libDeps: " + Arrays.toString(libDeps));

    try (final URLClassLoader classLoader = new URLClassLoader(ruleDeps, new URLClassLoader(libDeps != null ? libDeps : new URL[0], null))) {
      final LibraryFingerprint fingerprint = new LibraryFingerprint(classLoader, presents, absents, new MavenLogger(getLog()));
      fingerprint.toFile(destFile);
      if (getLog().isDebugEnabled())
        getLog().debug(fingerprint.toString());
    }
  }

  private void createPluginName() throws IOException, MojoExecutionException {
    final String pluginName = "sa.rule.name." + name;
    getLog().info("--> " + pluginName + " <--");
    final File nameFile = new File(getProject().getBuild().getOutputDirectory(), pluginName);
    for (final Artifact artifact : getProject().getDependencyArtifacts()) {
      final String adapterClassName = AssembleUtil.readFileFromJar(artifact.getFile(), "META-INF/services/io.opentracing.contrib.specialagent.Adapter");
      if (adapterClassName != null) {
        final String[] lines = adapterClassName.split("\n");
        for (String line : lines) {
          line = line.trim();
          if (line.length() > 0 && line.charAt(0) != '#') {
            Files.write(nameFile.toPath(), line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return;
          }
        }
      }
    }

    throw new MojoExecutionException("Dependency with adapter implementation was not found");
  }

  private String apiVersion;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (isSkip()) {
      getLog().info("Skipping plugin execution");
      return;
    }

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