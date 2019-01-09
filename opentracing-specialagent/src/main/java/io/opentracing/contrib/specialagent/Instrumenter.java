/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

/**
 * Enum representing an "instrumenter".
 *
 * @author Seva Safris
 */
public enum Instrumenter {
  BYTEMAN(new BytemanManager()),
  BYTEBUDDY(new ByteBuddyManager());

  final Manager manager;

  Instrumenter(final Manager manager) {
    this.manager = manager;
  }
}