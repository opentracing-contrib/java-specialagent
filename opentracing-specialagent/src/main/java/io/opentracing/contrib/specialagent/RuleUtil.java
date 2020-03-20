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
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public final class RuleUtil {
  private static String getArtifactFile(final File dir) {
    try {
      final MavenXpp3Reader reader = new MavenXpp3Reader();
      final Model model = reader.read(new FileReader(new File(dir, "pom.xml")));
      final String version = model.getVersion() != null ? model.getVersion() : model.getParent().getVersion();
      return model.getArtifactId() + "-" + version + ".jar";
    }
    catch (final IOException | XmlPullParserException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Filters the specified array of {@code File} objects by checking if the file
   * name is included in the specified set of files to match.
   *
   * @param files The array of {@code File} objects to filter.
   * @param matches The set of {@code File} objects whose names are to be
   *          matched by the specified array of URL objects.
   * @param index The index value for stack tracking (must be called with 0).
   * @param depth The depth value for stack tracking (must be called with 0).
   * @return An array of {@code File} objects that have file names that belong
   *         to the specified files to match.
   */
  private static File[] filterUrlFileNames(final File[] files, final Set<File> matches, final int index, final int depth) {
    for (int i = index; i < files.length; ++i) {
      final File file = files[i];
      final String artifact;
      if (file.isDirectory() && "target".equals(file.getParentFile().getName()) && "classes".equals(file.getName()))
        artifact = getArtifactFile(file.getParentFile().getParentFile());
      else if (file.isFile() && file.getName().endsWith(".jar"))
        artifact = file.getName();
      else
        continue;

      for (final File match : matches) {
        if (artifact.equals(match.getName())) {
          final File[] results = filterUrlFileNames(files, matches, i + 1, depth + 1);
          results[depth] = file;
          return results;
        }
      }
    }

    return depth == 0 ? null : new File[depth];
  }

  /**
   * Filter the specified array of {@code File} objects to return the
   * Instrumentation Rule files as specified by the Dependency TGF file at
   * {@code dependencyUrl}.
   *
   * @param files The array of {@code File} objects to filter.
   * @param dependenciesTgf The contents of the TGF file that specify the
   *          dependencies.
   * @param includeOptional Whether to include dependencies marked as
   *          {@code (optional)}.
   * @param scopes An array of Maven scopes to include in the returned set, or
   *          {@code null} to include all scopes.
   * @return An array of {@code File} objects representing Instrumentation Rule
   *         files.
   * @throws IOException If an I/O error has occurred.
   */
  public static File[] filterRuleURLs(final File[] files, final String dependenciesTgf, final boolean includeOptional, final String ... scopes) throws IOException {
    final Set<File> matches = AssembleUtil.selectFromTgf(dependenciesTgf, includeOptional, scopes);
    return filterUrlFileNames(files, matches, 0, 0);
  }

  private RuleUtil() {
  }
}