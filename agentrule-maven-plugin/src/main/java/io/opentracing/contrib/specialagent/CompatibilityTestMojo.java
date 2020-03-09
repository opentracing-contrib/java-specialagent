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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.lifecycle.internal.ProjectIndex;
import org.apache.maven.model.Dependency;
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

  @Parameter
  private List<CompatibilitySpec> passes;

  @Parameter
  private List<CompatibilitySpec> fails;

  private LibraryFingerprint fingerprint = null;

  private LibraryFingerprint getFingerprint() throws IOException {
    return fingerprint == null ? fingerprint = LibraryFingerprint.fromFile(new File(project.getBuild().getOutputDirectory(), UtilConstants.FINGERPRINT_FILE).toURI().toURL()) : fingerprint;
  }

  private static void rollbackArtifactVersion(final List<Dependency> artifacts, final Dependency[] rollback) {
    artifacts.clear();
    Collections.addAll(artifacts, rollback);
  }

  private static Dependency[] updateArtifactVersion(final List<Dependency> artifacts, final Dependency[] dependencies) {
    final Dependency[] rollback = artifacts.toArray(new Dependency[artifacts.size()]);
    artifacts.clear();
    Collections.addAll(artifacts, dependencies);
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

  private static String print(final Dependency ... dependencies) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < dependencies.length; ++i) {
      if (i > 0)
        builder.append(',');

      final Dependency dependency = dependencies[i];
      builder.append(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
    }

    return builder.toString();
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

  private void assertCompatibility(final Dependency[] dependencies, final boolean shouldPass) throws DependencyResolutionException, IOException, LifecycleExecutionException, MojoExecutionException {
    projectArtifactsCache.flush();
    final Dependency[] rollback = updateArtifactVersion(project.getDependencies(), dependencies);

    getLog().info("|-- " + print(dependencies));
    resolveDependencies(project);
    final List<URL> classpath = new ArrayList<>();
    for (final Artifact artifact : project.getArtifacts())
      classpath.add(MavenUtil.getPathOf(localRepository, artifact));

//    System.err.println(classpath);
    try (final URLClassLoader classLoader = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), null)) {
      final List<FingerprintError> errors = getFingerprint().isCompatible(classLoader);
      if (errors == null != shouldPass) {
        final String error = print(dependencies) + " should have " + (errors == null ? "failed" : "passed:\n" + AssembleUtil.toIndentedString(errors));
        if (failAtEnd)
          this.errors.add(error);
        else
          throw new MojoExecutionException(error + "\nClasspath:\n" + AssembleUtil.toIndentedString(classpath));
      }
    }

    rollbackArtifactVersion(project.getDependencies(), rollback);
  }

  private void assertCompatibility(final List<CompatibilitySpec> compatibilitySpecs, final boolean shouldPass) throws DependencyResolutionException, IOException, LifecycleExecutionException, MojoExecutionException, InvalidVersionSpecificationException {
    getLog().info("Running " + (shouldPass ? "PASS" : "FAIL") + " compatibility tests...");
    for (final CompatibilitySpec compatibilitySpec : compatibilitySpecs) {
      String versionSpec = null;
      int numSpecs = -1;
      final int size = compatibilitySpec.getDependencies().size();
      final List<Dependency>[] resolvedVersions = new List[size];
      for (int i = 0; i < size; ++i) {
        final ResolvableDependency dependency = compatibilitySpec.getDependencies().get(i);
        final boolean isSingleVersion = dependency.isSingleVersion();
        if (!isSingleVersion) {
          if (versionSpec == null)
            versionSpec = dependency.getVersion();
          else if (!versionSpec.equals(dependency.getVersion()))
            throw new MojoExecutionException("Version across all dependencies in a <pass> or <fail> spec must be equal");
        }

        resolvedVersions[i] = dependency.resolveVersions(project, getLog());
        if (!isSingleVersion) {
          if (numSpecs == -1)
            numSpecs = resolvedVersions[i].size();
          else if (numSpecs != resolvedVersions[i].size())
            throw new MojoExecutionException("Expeted the same number of resolved versions for: " + compatibilitySpec);
        }
      }

      if (numSpecs == -1)
        numSpecs = 1;

      for (int i = 0; i < numSpecs; ++i) {
        final Dependency[] dependencies = new Dependency[resolvedVersions.length];
        for (int j = 0; j < resolvedVersions.length; ++j)
          dependencies[j] = resolvedVersions[j].get(resolvedVersions[j].size() == numSpecs ? i : 0);

        assertCompatibility(dependencies, shouldPass);
      }
    }
  }

  private List<Dependency> getFingerprintedDependencies() {
    final List<Dependency> dependencies = new ArrayList<>();
    for (final Dependency dependency : project.getDependencies())
      if (dependency.isOptional())
        dependencies.add(dependency);

    return dependencies;
  }

  private static boolean isRunning;

  private static List<CompatibilitySpec> shortFormToLongForm(final String str) {
    final String[] specs = str.split(";");
    final List<CompatibilitySpec> result = new ArrayList<>();
    for (final String spec : specs)
      result.add(new CompatibilitySpec(Collections.singletonList(ResolvableDependency.parse(spec))));

    return result;
  }

  @Override
  public void execute() throws MojoExecutionException {
    if (isRunning)
      return;

    isRunning = true;
    if ("pom".equalsIgnoreCase(project.getPackaging())) {
      getLog().info("Skipping for \"pom\" module.");
      return;
    }

    if ((passCompatibility != null || failCompatibility != null) && (passes != null || fails != null))
      throw new MojoExecutionException("<{pass/fail}Compatibility> cannot be used in conjuction with <passes> or <fails>");

    if (passCompatibility != null)
      passes = shortFormToLongForm(passCompatibility);

    if (failCompatibility != null)
      fails = shortFormToLongForm(failCompatibility);

    try {
      boolean wasRun = false;
      if (passes != null && (wasRun = true))
        assertCompatibility(passes, true);

      if (fails != null && (wasRun = true))
        assertCompatibility(fails, false);

      if (failAtEnd && errors.size() > 0)
        throw new MojoExecutionException("Failed compatibility tests:\n" + AssembleUtil.toIndentedString(errors));

      if (wasRun) {
        // Reset the dependencies back to original
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