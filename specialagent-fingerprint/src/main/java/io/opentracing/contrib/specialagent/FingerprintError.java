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
  static enum Reason {
    MUST_BE_PRESENT,
    MUST_BE_ABSENT,
    MISSING,
    MISMATCH
  }

  private final Reason reason;
  private final NamedFingerprint<?> expected;
  private final NamedFingerprint<?> actual;

  /**
   * Creates a new {@code FingerprintError} with the specified {@code Reason}
   * and provided {@code NamedFingerprint} objects for which the failure
   * occurred.
   *
   * @param reason The reason for the error.
   * @param expected The expected {@code NamedFingerprint} object.
   * @param actual The actual {@code NamedFingerprint} object.
   */
  FingerprintError(final Reason reason, final NamedFingerprint<?> expected, final NamedFingerprint<?> actual) {
    this.reason = reason;
    this.expected = expected;
    this.actual = actual;
  }

  @Override
  public String toString() {
    if (reason == Reason.MUST_BE_PRESENT || reason == Reason.MUST_BE_ABSENT || reason == Reason.MISSING)
      return " " + reason + " " + expected.getName();

    return reason + " " + expected.getName() + " (expected <> actual):\n(expected) " + expected.toString().replace("\n", "\n    ") + "\n  (actual) " + actual.toString().replace("\n", "\n    ");
  }
}