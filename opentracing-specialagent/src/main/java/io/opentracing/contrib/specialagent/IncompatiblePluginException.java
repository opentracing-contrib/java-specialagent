/* Copyright 2019 The OpenTracing Authors
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
 * Thrown to indicate a particular class name representing an Instrumentation
 * Rule and Plugin is not compatible with the application runtime.
 *
 * @author Seva Safris
 */
public class IncompatiblePluginException extends IllegalStateException {
  private static final long serialVersionUID = -85760513192900860L;

  /**
   * Constructs a new {@link IncompatiblePluginException} with the specified
   * class name.
   *
   * @param className The class name.
   */
  public IncompatiblePluginException(final String className) {
    super(className);
  }
}