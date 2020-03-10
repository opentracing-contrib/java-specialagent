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
 * A {@code RuntimeException} used to signal an early return from a visitor
 * method annotated with {@link net.bytebuddy.asm.Advice.OnMethodEnter}. This
 * exception is thereafter intended to be caught by a subsequent visitor method
 * annotated with {@link net.bytebuddy.asm.Advice.OnMethodExit}.
 *
 * @author Seva Safris
 */
public class EarlyReturnException extends RuntimeException {
  private static final long serialVersionUID = -6230625173943091335L;

  private final Object returnValue;

  /**
   * Creates a new {@code EarlyReturnException} with the specified return value.
   *
   * @param returnValue The value to return early.
   */
  public EarlyReturnException(final Object returnValue) {
    super(null, null, false, false);
    this.returnValue = returnValue;
  }

  /**
   * Creates a new {@code EarlyReturnException} with a null return value.
   */
  public EarlyReturnException() {
    this(null);
  }

  /**
   * @return The value to return early.
   */
  public Object getReturnValue() {
    return this.returnValue;
  }

  /**
   * No-op override of {@link Throwable#fillInStackTrace()} for improved
   * performance.
   *
   * @return A reference to this {@code Throwable} instance.
   */
  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}