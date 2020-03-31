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
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.tree.TGFDependencyNodeVisitor;
import org.apache.maven.plugins.dependency.tree.TreeMojo;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

/**
 * Mojo that fingerprints 3rd-party library bytecode to ensure compatibility of
 * Integration Rules. The implementation uses introspection to record the
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
        final URL url = AssembleUtil.toURL(MavenUtil.getPathOf(localRepository.getBasedir(), dependency));
        final URL[] urls = getDependencyPaths(localRepository, scope, optional, iterator, depth + 1);
        if (urls != null && url != null)
          urls[depth] = url;

        return urls;
      }
    }

    return depth == 0 ? null : new URL[depth];
  }

  @Inject
  private MojoExecutor executor;

  @Inject
  private ProjectDependenciesResolver projectDependenciesResolver;

  @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
  private ArtifactRepository localRepository;

  @Parameter(defaultValue = "${mojoExecution}", required = true, readonly = true)
  private MojoExecution execution;

  @Parameter(defaultValue = "${sa.rule.name}", required = true)
  private String name;

  @Parameter(property = "debug")
  private boolean debug = false;

  @Parameter
  private List<IsolatedDependency> isolatedDependencies;

  @Parameter
  private List<String> presents;

  @Parameter
  private List<String> absents;

  MavenSession getSession() {
    return (MavenSession)HackMojo.getField(TreeMojo.class, this, "session");
  }

  private class CustomNodeVisitor implements DependencyNodeVisitor {
    private final DependencyNodeVisitor target;

    private CustomNodeVisitor(final DependencyNodeVisitor target) {
      this.target = target;
    }

    @Override
    public boolean visit(final DependencyNode node) {
      final List<DependencyNode> children = new ArrayList<>(node.getChildren());
      for (final Iterator<DependencyNode> iterator = children.iterator(); iterator.hasNext();) {
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
    return visitor instanceof TGFDependencyNodeVisitor ? new CustomNodeVisitor(visitor) : visitor;
  }

  private void createDependenciesTgf() throws DependencyResolutionException, IOException, LifecycleExecutionException, MojoExecutionException, MojoFailureException {
    final File file = new File(getProject().getBuild().getOutputDirectory(), "dependencies.tgf");
    getLog().info("--> dependencies.tgf <--");
    HackMojo.setField(TreeMojo.class, this, "outputType", "tgf");
    HackMojo.setField(TreeMojo.class, this, "outputFile", file);
    super.execute();

    if (isolatedDependencies == null || isolatedDependencies.size() == 0)
      return;

    try (final RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
      for (String line; (line = raf.readLine()) != null;) {
        if ("#".equals(line)) {
          raf.seek(raf.getFilePointer() - 3);
          for (final IsolatedDependency isolatedDependency : isolatedDependencies) {
            if (isolatedDependency.getVersion() != null)
              throw new MojoExecutionException("Version of " + isolatedDependency + " must be specified in <dependencyManagement>");

            isolatedDependency.setVersion(MavenUtil.lookupVersion(getProject(), isolatedDependency));
            raf.write(("\n0 " + isolatedDependency.getGroupId() + ":" + isolatedDependency.getArtifactId() + ":jar:" + isolatedDependency.getVersion() + ":isolated").getBytes());
          }

          raf.write("\n#".getBytes());
          raf.setLength(raf.getFilePointer());

          final Dependency[] rollback = MutableMojo.replaceDependencies(getProject().getDependencies(), MavenDependency.toDependencies(isolatedDependencies));
          MutableMojo.resolveDependencies(getSession(), execution, executor, projectDependenciesResolver);
          MutableMojo.rollbackDependencies(getProject().getDependencies(), rollback);
          MutableMojo.resolveDependencies(getSession(), execution, executor, projectDependenciesResolver);
          return;
        }
      }
    }

    throw new MojoExecutionException("Could not write isolated dependencies into dependencies.tgf");
  }

  private void createLocalRepoFile() throws IOException {
    final File file = new File(getProject().getBuild().getDirectory(), "localRepository.txt");
    Files.write(file.toPath(), localRepository.getBasedir().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private void createFingerprintBin() throws IOException {
    getLog().info("--> fingerprint.bin <--");
    final File destFile = new File(getProject().getBuild().getOutputDirectory(), UtilConstants.FINGERPRINT_FILE);
    destFile.getParentFile().mkdirs();

    // The `ruleDeps` represent the Integration (this is the dependency(ies)
    // that bridges/links between the 3rd-Party Library to the Integration Rule).
    final URL[] ruleDeps = getDependencyPaths(localRepository, "compile", true, getProject().getArtifacts().iterator(), 1);
    // Include the compile path of the Integration Rule itself, which solves the use-
    // case where there is no Integration (i.e. the Integration Rule directly
    // bridges/links between the 3rd-Party Library to itself).
    ruleDeps[0] = AssembleUtil.toURL(new File(getProject().getBuild().getOutputDirectory()));
    if (debug)
      getLog().warn("ruleDeps: " + Arrays.toString(ruleDeps));

    // The `libDeps` represent the 3rd-Party Library that is being instrumented
    final URL[] libDeps = getDependencyPaths(localRepository, "provided", true, getProject().getArtifacts().iterator(), 0);
    if (debug) {
      getLog().warn("libDeps: " + Arrays.toString(libDeps));
      getLog().warn("presents: " + presents);
      getLog().warn("absents: " + absents);
    }

    final LibraryFingerprint fingerprint = fingerprint(ruleDeps, libDeps, presents, absents, new MavenLogger(getLog()));
    fingerprint.toFile(destFile);
    if (debug)
      getLog().warn(fingerprint.toString());
  }

  static LibraryFingerprint fingerprint(final URL[] ruleDeps, final URL[] libDeps, final List<String> presents, final List<String> absents, final Logger logger) throws IOException {
    try (final URLClassLoader classLoader = new URLClassLoader(ruleDeps, new URLClassLoader(libDeps != null ? libDeps : new URL[0], null))) {
      return new LibraryFingerprint(classLoader, presents, absents, logger);
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
        for (int i = 0; i < lines.length; ++i) {
          final String line = lines[i].trim();
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
  private static boolean isRunning;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (isRunning)
      return;

    isRunning = true;
    if (isSkip()) {
      getLog().info("Skipping plugin execution");
      return;
    }

    if ("pom".equalsIgnoreCase(getProject().getPackaging())) {
      getLog().info("Skipping for \"pom\" module.");
      return;
    }

    try {
      for (final Artifact artifact : getProject().getDependencyArtifacts()) {
        if ("io.opentracing".equals(artifact.getGroupId()) && "opentracing-api".equals(artifact.getArtifactId())) {
          apiVersion = artifact.getVersion();
          break;
        }
      }

      createDependenciesTgf();
      createFingerprintBin();
      createLocalRepoFile();
      createPluginName();
    }
    catch (final DependencyResolutionException | IOException | LifecycleExecutionException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
    finally {
      isRunning = false;
    }
  }
}