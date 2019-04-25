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
package io.opentracing.contrib.specialagent.httpclient;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentracing.contrib.specialagent.AgentRule;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class HttpClientAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs, final AgentBuilder builder) {
    final Narrowable narrowable = builder
        .type(hasSuperType(named("org.apache.http.client.HttpClient")));
    return Arrays.asList(narrowable
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder,
              final TypeDescription typeDescription, final ClassLoader classLoader,
              final JavaModule module) {
            return builder.visit(
                Advice.to(HttpClientAgentRule.class).on(named("execute")));
          }
        }), narrowable.transform(new Transformer() {
      @Override
      public Builder<?> transform(Builder<?> builder, TypeDescription typeDescription,
          ClassLoader classLoader, JavaModule module) {
        return builder.visit(
            Advice.to(OnException.class).on(named("execute")));
      }
    }));
  }

  @Advice.OnMethodEnter
  public static void enter(final @Advice.Origin String origin,
      final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg,
      @Advice.Argument(value = 1, optional = true, readOnly = false, typing = Typing.DYNAMIC) Object arg2,
      @Advice.Argument(value = 2, optional = true, readOnly = false, typing = Typing.DYNAMIC) Object arg3) {
    if (isEnabled(origin)) {
      Object[] objects = HttpClientAgentIntercept.enter(arg, arg2, arg3);
      if(objects != null) {
        if(objects.length == 1) {
          arg2 = objects[0];
        } else if(objects.length == 2) {
          arg3 = objects[1];
        }
      }
    }
  }

  @Advice.OnMethodExit
  public static void exit(final @Advice.Origin String origin,
      final @Advice.Return(typing = Typing.DYNAMIC) Object returned) {
    if (isEnabled(origin)) {
      HttpClientAgentIntercept.exit(returned);
    }
  }

  public static class OnException {
    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Thrown(typing = Typing.DYNAMIC) Throwable thrown) {
      if (thrown != null) {
        HttpClientAgentIntercept.onError(thrown);
      }
    }
  }
}