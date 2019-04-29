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

package io.opentracing.contrib.specialagent.thrift;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class ThriftAsyncMethodCallbackAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    final Narrowable narrowable = builder
      .type(hasSuperType(named("org.apache.thrift.async.AsyncMethodCallback")));

    return Arrays.asList(
      narrowable.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnComplete.class).on(named("onComplete")));
        }
      }), narrowable.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnError.class).on(named("onError")));
        }
      }));
  }

  public static class OnComplete {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin) {
      if (isEnabled(origin))
        ThriftAsyncMethodCallbackAgentIntercept.onComplete();
    }
  }

  public static class OnError {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, @Advice.Argument(value = 0) Object exception) {
      if (isEnabled(origin))
        ThriftAsyncMethodCallbackAgentIntercept.onError(exception);
    }
  }
}