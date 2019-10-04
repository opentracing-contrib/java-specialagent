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

package io.opentracing.contrib.specialagent;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

import io.opentracing.contrib.okhttp3.TracingInterceptor;
import okhttp3.OkHttpClient;

public class OkHttpFingerprintTest {
  private static final Logger logger = Logger.getLogger(OkHttpFingerprintTest.class);

  @Test
  public void test() throws IOException {
    FingerprintBuilder.debugVisitor = false;
    Logger.setLevel(Level.FINEST);

    final LibraryFingerprint fingerprint = new LibraryFingerprint(new URLClassLoader(new URL[] {TracingInterceptor.class.getProtectionDomain().getCodeSource().getLocation()}, new URLClassLoader(new URL[] {OkHttpClient.class.getProtectionDomain().getCodeSource().getLocation()})), null, null, logger);
    System.out.println(fingerprint.toString());
  }
}