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

/**
 * Represents a function that accepts three arguments and produces a result.
 * This is the four-arity specialization of {@link Function}. This is a
 * functional interface. whose functional method is
 * {@link #apply(Object,Object,Object,Object)}.
 *
 * @param <A> The type of the first argument to the function.
 * @param <B> The type of the second argument to the function.
 * @param <C> The type of the third argument to the function.
 * @param <D> The type of the fourth argument to the function.
 * @param <R> The type of the result of the function.
 * @param <T> The type of the throwable.
 */
public interface QuadFunction<A,B,C,D,R,T extends Throwable> {
  /**
   * Applies this function to the given arguments.
   *
   * @param a The first function argument.
   * @param b The second function argument.
   * @param c The third function argument.
   * @param d The fourth function argument.
   * @return The function result.
   * @throws T The throwable.
   */
  R apply(A a, B b, C c, D d) throws T;
}