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

import static io.opentracing.contrib.specialagent.DefaultAgentRule.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentracing.contrib.specialagent.DefaultAgentRule.DefaultLevel;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class TracerExclusionAgent {
  public static final MutexLatch latch = AgentRule.$Access.mutexLatch();

  public static AgentBuilder premain(final String[] traceExcludedClasses, final AgentBuilder builder) {
    log("\n<<<<<<<<<<<<<<< Installing TracerExclusionAgent >>>>>>>>>>>>>>>>\n", null, DefaultLevel.FINE);
    if (traceExcludedClasses == null || traceExcludedClasses.length == 0)
      return null;

    try {
      final Narrowable narrowable = builder
        .type(hasSuperType(named(traceExcludedClasses[0])));

      for (int i = 1; i < traceExcludedClasses.length; ++i)
        narrowable.or(hasSuperType(named(traceExcludedClasses[i])));

      return narrowable.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(TracerExclusionAgent.class).on(isPublic().and(any())));
        }});
    }
    finally {
      log("\n>>>>>>>>>>>>>>>> Installed TracerExclusionAgent <<<<<<<<<<<<<<<<\n", null, DefaultLevel.FINE);
    }
  }

  @Advice.OnMethodEnter
  public static void enter() {
    latch.set(latch.get() + 1);
  }

  @Advice.OnMethodExit
  public static void exit() {
    latch.set(latch.get() - 1);
  }
}