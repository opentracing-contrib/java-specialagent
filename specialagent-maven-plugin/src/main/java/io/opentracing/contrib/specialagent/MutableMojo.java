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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.lifecycle.internal.ProjectIndex;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;

public abstract class MutableMojo extends AbstractMojo {
  @Inject
  private MojoExecutor executor;

  @Inject
  private ProjectDependenciesResolver projectDependenciesResolver;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${mojoExecution}", required = true, readonly = true)
  private MojoExecution execution;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Parameter(property = "maven.test.skip")
  private boolean mavenTestSkip = false;

  @Parameter(property="skipTests")
  private boolean skipTests = false;

  @Parameter(property = "debug")
  private boolean debug = false;

  final MavenProject getProject() {
    return project;
  }

  final boolean isDebug() {
    return debug;
  }

  static void rollbackDependencies(final List<Dependency> target, final Dependency[] source) {
    target.clear();
    Collections.addAll(target, source);
  }

  static Dependency[] replaceDependencies(final List<Dependency> target, final Dependency[] source) {
    final Dependency[] rollback = target.toArray(new Dependency[target.size()]);
    rollbackDependencies(target, source);
    return rollback;
  }

  private static void flushProjectArtifactsCache(final MojoExecutor executor) {
    try {
      final Field lifeCycleDependencyResolverField = executor.getClass().getDeclaredField("lifeCycleDependencyResolver");
      lifeCycleDependencyResolverField.setAccessible(true);
      final Object lifeCycleDependencyResolver = lifeCycleDependencyResolverField.get(executor);
      final Field projectArtifactsCacheField = lifeCycleDependencyResolver.getClass().getDeclaredField("projectArtifactsCache");
      projectArtifactsCacheField.setAccessible(true);
      final Object projectArtifactsCache = projectArtifactsCacheField.get(lifeCycleDependencyResolver);
      final Method flushMethod = projectArtifactsCache.getClass().getDeclaredMethod("flush");
      flushMethod.invoke(projectArtifactsCache);
    }
    catch (final IllegalAccessException | InvocationTargetException | NoSuchFieldException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  void resolveDependencies() throws DependencyResolutionException, LifecycleExecutionException {
    resolveDependencies(session, execution, executor, projectDependenciesResolver);
  }

  static void resolveDependencies(final MavenSession session, final MojoExecution execution, final MojoExecutor executor, final ProjectDependenciesResolver projectDependenciesResolver) throws DependencyResolutionException, LifecycleExecutionException {
//    flushProjectArtifactsCache(executor);

    final MavenProject project = session.getCurrentProject();
    final Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();
    final Map<String,List<MojoExecution>> executions = new LinkedHashMap<>(execution.getForkedExecutions());
    final ExecutionListener executionListener = session.getRequest().getExecutionListener();
    try {
      project.setDependencyArtifacts(null);
      execution.getForkedExecutions().clear();
      session.getRequest().setExecutionListener(null);
      executor.execute(session, Collections.singletonList(execution), new ProjectIndex(session.getProjects()));
    }
    finally {
      execution.getForkedExecutions().putAll(executions);
      session.getRequest().setExecutionListener(executionListener);
      project.setDependencyArtifacts(dependencyArtifacts);
    }

    projectDependenciesResolver.resolve(newDefaultDependencyResolutionRequest(session));
  }

  private static DefaultDependencyResolutionRequest newDefaultDependencyResolutionRequest(final MavenSession session) {
    try {
      for (final Constructor<?> constructor : DefaultDependencyResolutionRequest.class.getConstructors())
        if (constructor.getParameterTypes().length == 2 && constructor.getParameterTypes()[0] == MavenProject.class)
          return (DefaultDependencyResolutionRequest)constructor.newInstance(session.getCurrentProject(), getRepositorySystemSession(session));

      return null;
    }
    catch (final IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object getRepositorySystemSession(final MavenSession session) {
    try {
      final Field field = session.getClass().getDeclaredField("repositorySession");
      field.setAccessible(true);
      return field.get(session);
    }
    catch (final IllegalAccessException | NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isRunning;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (isRunning)
      return;

    if ("pom".equalsIgnoreCase(project.getPackaging())) {
      getLog().info("Skipping for \"pom\" module.");
      return;
    }

    if (MavenUtil.shouldSkip(execution, mavenTestSkip || skipTests || isSkip() != null)) {
      final StringBuilder builder = new StringBuilder("Tests are skipped (");
      if (mavenTestSkip)
        builder.append("maven.test.skip=true; ");

      if (skipTests)
        builder.append("skipTests=true; ");

      if (isSkip() != null)
        builder.append(isSkip() + "=true; ");

      builder.setLength(builder.length() - 2);
      builder.append(")");

      getLog().info(builder.toString());
      return;
    }

    try {
      isRunning = true;
      doExecute();
    }
    finally {
      isRunning = false;
    }
  }

  abstract String isSkip();
  abstract void doExecute() throws MojoExecutionException, MojoFailureException;
}