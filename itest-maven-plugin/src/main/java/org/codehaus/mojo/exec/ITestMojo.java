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

package org.codehaus.mojo.exec;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import io.opentracing.contrib.specialagent.AssembleUtil;
import io.opentracing.contrib.specialagent.BiConsumer;

@Mojo(name = "itest", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST, threadSafe = true)
@Execute(goal = "itest")
public final class ITestMojo extends ExecMojo {
  @Parameter(required = true)
  private List<String> mainClasses;

  private String mainClass;

  @Override
  protected void collectProjectArtifactsAndClasspath(final List<Artifact> artifacts, final List<File> theClasspathFiles) {
    super.collectProjectArtifactsAndClasspath(artifacts, theClasspathFiles);
  }

  @Override
  CommandLine getExecutablePath(final Map<String,String> enviro, final File dir) {
    final CommandLine cli = super.getExecutablePath(enviro, dir);
    return new CommandLine(cli) {
      @Override
      public CommandLine addArguments(final String[] addArguments, final boolean handleQuoting) {
        super.addArguments(addArguments, handleQuoting);
        final CommandLine cli = super.addArgument(mainClass);
        getLog().info(cli.toString().replace(", ", " "));
        return cli;
      }
    };
  }

  @Override
  public void execute() throws MojoExecutionException {
    setExecutable("java");
    classpathScope = "test";

    final List<Artifact> artifacts = new ArrayList<>();
    final List<File> theClasspathFiles = new ArrayList<>();
    collectProjectArtifactsAndClasspath(artifacts, theClasspathFiles);

    final List<Object> mains = new ArrayList<>();
    try {
      final List<URL> classpathURLs = new ArrayList<>();
      for (final File classpathFile : theClasspathFiles)
        if (classpathFile.exists())
          classpathURLs.add(classpathFile.getAbsoluteFile().toURI().toURL());

      for (final Artifact artifact : artifacts)
        classpathURLs.add(artifact.getFile().toURI().toURL());

      final URL[] classpath = classpathURLs.toArray(new URL[classpathURLs.size()]);

      boolean hasRegex = false;
      for (final String mainClass : mainClasses) {
        if (mainClass.indexOf('?') != -1 || mainClass.indexOf('*') != -1) {
          final String regex = AssembleUtil.convertToRegex(mainClass);
          final List<String> entry = new ArrayList<>();
          entry.add(regex);
          mains.add(entry);
          hasRegex = true;
        }
        else {
          mains.add(mainClass);
        }
      }

      if (hasRegex) {
        final URLClassLoader classLoader = new URLClassLoader(classpath);
        AssembleUtil.<Void>forEachClass(classpath, null, new BiConsumer<String,Void>() {
          @Override
          public void accept(final String name, final Void arg) {
            try {
              for (final Object main : mains) {
                if (main instanceof List) {
                  final List<String> list = (List<String>)main;
                  final String regex = list.get(0);
                  final String className = name.substring(0, name.length() - 6).replace('/', '.');
                  if (className.matches(regex)) {
                    final Class<?> cls = Class.forName(className, false, classLoader);
                    for (final Method method : cls.getMethods()) {
                      if (Modifier.isStatic(method.getModifiers()) && "main".equals(method.getName()) && Arrays.equals(method.getParameterTypes(), new Class[] {String[].class})) {
                        list.add(className);
                        break;
                      }
                    }
                  }
                }
              }
            }
            catch (final ClassNotFoundException e) {
              throw new IllegalStateException(e);
            }
          }
        });
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException();
    }

    boolean executed = false;
    for (final Object main : mains) {
      if (main instanceof List) {
        final List<String> list = (List<String>)main;
        for (int i = 1; i < list.size(); ++i) {
          mainClass = list.get(i);
          executed = true;
          super.execute();
        }
      }
      else {
        mainClass = (String)main;
        executed = true;
        super.execute();
      }
    }

    if (!executed)
      throw new MojoExecutionException("No appliations were found");
  }
}