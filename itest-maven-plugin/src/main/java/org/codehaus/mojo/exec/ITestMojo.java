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
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "itest", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST, threadSafe = true)
@Execute(goal = "itest")
public final class ITestMojo extends ExecMojo {
  @Parameter(required = true)
  private String mainClass;

  @Override
  CommandLine getExecutablePath(final Map<String,String> enviro, final File dir) {
    final CommandLine cli = super.getExecutablePath(enviro, dir);
    return new CommandLine(cli) {
      @Override
      public CommandLine addArguments(final String[] addArguments, final boolean handleQuoting) {
        super.addArguments(addArguments, handleQuoting);
        return super.addArgument(mainClass);
      }
    };
  }

  @Override
  public void execute() throws MojoExecutionException {
    setExecutable("java");
    classpathScope = "test";
    super.execute();
  }
}