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

package io.opentracing.contrib.specialagent.rule.lettuce52;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class Lettuce52AgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(not(isInterface()).and(hasSuperType(named("io.lettuce.core.api.StatefulRedisConnection")).and(not(nameStartsWith("io.opentracing.contrib.redis.lettuce")))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(StatefulRedis.class).on(named("async")));
        }})
      .type(not(isInterface()).and(hasSuperType(named("io.lettuce.core.cluster.api.StatefulRedisClusterConnection")).and(not(nameStartsWith("io.opentracing.contrib.redis.lettuce")))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(StatefulRedisCluster.class).on(named("async")));
        }})
      .type(not(isInterface()).and(hasSuperType(named("io.lettuce.core.pubsub.StatefulRedisPubSubConnection")).and(not(nameStartsWith("io.opentracing.contrib.redis.lettuce")))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AddPubSubListener.class).on(named("addListener")));
        }}));
  }

  public static class StatefulRedis {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled(Lettuce52AgentRule.class, origin) && !WrapperProxy.isWrapper(returned))
        returned = WrapperProxy.wrap(returned, Lettuce52AgentIntercept.getAsyncCommands(returned));
    }
  }

  public static class StatefulRedisCluster {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled(Lettuce52AgentRule.class, origin) && !WrapperProxy.isWrapper(returned))
        returned = WrapperProxy.wrap(returned, Lettuce52AgentIntercept.getAsyncClusterCommands(returned));
    }
  }

  public static class AddPubSubListener {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object arg) {
      if (isEnabled(Lettuce52AgentRule.class, origin) && !WrapperProxy.isWrapper(arg))
        arg = WrapperProxy.wrap(arg, Lettuce52AgentIntercept.addPubSubListener(arg));
    }
  }
}