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

import java.lang.instrument.Instrumentation;

public interface DeferredAttach {
  /**
   * @param inst The {@code Instrumentation}.
   * @return If this method returns {@code true} for any enabled
   *         {@link AgentRule}s, the SpecialAgent will delegate the invocation
   *         of the {@code init} {@link Runnable} to the first {@link AgentRule}
   *         that triggers the deferred initialization. If this method returns
   *         {@code false} for all {@link AgentRule}s, the SpecialAgent will
   *         invoke {@code init} immediately.
   */
  boolean isDeferrable(Instrumentation inst);
}