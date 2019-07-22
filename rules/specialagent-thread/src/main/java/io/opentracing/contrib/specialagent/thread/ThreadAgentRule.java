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

package io.opentracing.contrib.specialagent.thread;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class ThreadAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(hasSuperType(named("java.lang.Thread")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder
            .visit(Advice.to(Start.class).on(named("start")))
            .visit(Advice.to(Run.class).on(named("run")))
            .visit(Advice.to(RunError.class).on(named("run")));
        }}));
  }

  public static class Start {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz) {
      if (isEnabled(origin))
        ThreadAgentIntercept.start(thiz);
    }
  }

  public static class Run {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz) {
      if (isEnabled(origin))
        ThreadAgentIntercept.runEnter(thiz);
    }

    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, final @Advice.This Object thiz) {
      if (isEnabled(origin))
        ThreadAgentIntercept.runExit(thiz);
    }
  }

  public static class RunError {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown, final @Advice.This Object thiz) {
      if (isEnabled(origin) && thrown != null)
        ThreadAgentIntercept.runExit(thiz);
    }
  }
}