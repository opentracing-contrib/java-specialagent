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

/**
 * Class representing an error when performing a fingerprint compatibility test.
 *
 * @author Seva Safris
 */
class FingerprintError {
  /**
   * An enum representing the reason for the error.
   */
  public static enum Reason {
    MISSING,
    MISMATCH
  }

  private final Reason reason;
  private final ClassFingerprint a;
  private final ClassFingerprint b;

  /**
   * Creates a new {@code FingerprintError} with the specified {@code Reason}
   * and provided {@code ClassFingerprint} objects for which the failure
   * occurred.
   *
   * @param reason The reason for the error.
   * @param a The first {@code ClassFingerprint} object.
   * @param b The second {@code ClassFingerprint} object, or {@code null} if the
   *          fingerprint is missing.
   */
  FingerprintError(final Reason reason, final ClassFingerprint a, final ClassFingerprint b) {
    this.reason = reason;
    this.a = a;
    this.b = b;
  }

  @Override
  public String toString() {
    return reason == Reason.MISSING ? " " + reason + " " + a.getName() : (reason + " " + a.getName() + " (a <> b):\n(a) " + a.toString().replace("\n", "\n    ") + "\n(b) " + b.toString().replace("\n", "\n    "));
  }
}