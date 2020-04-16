/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.lettuce;

import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class LettuceAgentRule extends AgentRule {
  @Override
  public AgentBuilder[] buildAgentUnchained(final AgentBuilder builder) {
    return new AgentBuilder[] {builder
      .type(not(isInterface()).and(named("io.lettuce.core.RedisClient")))
      .transform(new Transformer() {
        @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(Connect.class).on(nameStartsWith("connect").and(nameEndsWith("Async")).and(takesArgument(1, named("io.lettuce.core.RedisURI")).and(returns(named("io.lettuce.core.ConnectionFuture"))))));
        }})
      .type(not(isInterface()).and(hasSuperType(named("io.lettuce.core.AbstractRedisAsyncCommands"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(AsyncCommands.class).on(named("dispatch").and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand")))));
        }})
      .type(not(isInterface()).and(hasSuperType(named("io.lettuce.core.AbstractRedisReactiveCommands"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(ReactiveCommandsMono.class).on(named("createMono").and(takesArgument(0, named("java.util.function.Supplier"))).and(returns(named("reactor.core.publisher.Mono")))));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(ReactiveCommandsFlux.class).on(nameStartsWith("create").and(nameEndsWith("Flux")).and(takesArgument(0, named("java.util.function.Supplier"))).and(returns(named("reactor.core.publisher.Flux")))));
        }})
    };
  }

  public static class Connect {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 1, typing = Typing.DYNAMIC) Object arg) {
      if (isAllowed(className, origin))
        LettuceAgentIntercept.connectStart(arg);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Return(typing = Typing.DYNAMIC) Object returned, final @Advice.Thrown Throwable thrown) {
      if (isAllowed(className, origin))
        LettuceAgentIntercept.connectEnd(returned, thrown);
    }
  }

  public static class AsyncCommands {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg) {
      if (isAllowed(className, origin))
        LettuceAgentIntercept.dispatchStart(arg);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg, final @Advice.Return(typing = Typing.DYNAMIC) Object returned, final @Advice.Thrown Throwable thrown) {
      if (isAllowed(className, origin))
       LettuceAgentIntercept.dispatchEnd(arg, returned, thrown);
    }
  }

  public static class ReactiveCommandsMono {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isAllowed(className, origin))
        returned = LettuceAgentIntercept.createMonoEnd(arg, returned);
    }
  }

  public static class ReactiveCommandsFlux {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isAllowed(className, origin))
        returned = LettuceAgentIntercept.createFluxEnd(arg, returned);
    }
  }
}