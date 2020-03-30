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

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("Intended to be used for hands-on debugging")
public class FingerprintMojoTest {
  private static final Logger logger = Logger.getLogger(FingerprintMojoTest.class);
  private static final String version = MavenUtil.getArtifactVersion(new File("").getAbsoluteFile());
  private static final String localRepositoryPath = System.getProperty("user.home") + "/.m2/repository";

  private static URL getPath(final MavenDependency dependency) throws MalformedURLException {
    return new URL("file", "", MavenUtil.getPathOf(localRepositoryPath, dependency));
  }

  @Test
  public void testJms1() throws IOException {
    final URL[] ruleDeps = new URL[] {
      getPath(new MavenDependency("io.opentracing.contrib.specialagent.rule", "jms-1", version)),
      getPath(new MavenDependency("io.opentracing.contrib", "opentracing-jms-1", "0.1.7")),
      getPath(new MavenDependency("io.opentracing.contrib", "opentracing-jms-common", "0.1.7"))
    };
    final URL[] libDeps = new URL[] {
      getPath(new MavenDependency("javax.jms", "jms-api", "1.1-rev-1"))
    };

    final LibraryFingerprint fingerprint = FingerprintMojo.fingerprint(ruleDeps, libDeps, null, null, logger);
    System.out.println(fingerprint.toString());
    assertNotEquals("", fingerprint.toString());
  }

  @Test
  public void testJms2() throws IOException {
    final URL[] ruleDeps = new URL[] {
      getPath(new MavenDependency("io.opentracing.contrib.specialagent.rule", "jms-2", version)),
      getPath(new MavenDependency("io.opentracing.contrib", "opentracing-jms-2", "0.1.7")),
      getPath(new MavenDependency("io.opentracing.contrib", "opentracing-jms-common", "0.1.7"))
    };
    final URL[] libDeps = new URL[] {
      getPath(new MavenDependency("javax.jms", "javax.jms-api", "2.0.1"))
    };

    final LibraryFingerprint fingerprint = FingerprintMojo.fingerprint(ruleDeps, libDeps, null, null, logger);
    System.out.println(fingerprint.toString());
    assertNotEquals("", fingerprint.toString());
  }
}