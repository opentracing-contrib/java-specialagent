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

package io.opentracing.contrib.specialagent.rule.pulsar.client;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class PulsarClientAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(not(isInterface()).and(hasSuperType(named("org.apache.pulsar.client.impl.ProducerBase"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(Producer.class).on(named("internalSendAsync").and(takesArguments(1))));
        }})
    .type(not(isInterface()).and(hasSuperType(named("org.apache.pulsar.client.api.Consumer"))))
    .transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
        return builder
            .visit(Advice.to(Consumer.class).on(named("receive")))
            .visit(Advice.to(ConsumerAsync.class).on(named("receiveAsync")));
      }}));
  }

  public static class Consumer {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, @Advice.This(typing = Typing.DYNAMIC) Object thiz, final @Advice.Return Object returned) {
      if (isEnabled(origin))
        PulsarClientAgentIntercept.receiveEnd(thiz, returned);
    }
  }

  public static class ConsumerAsync {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, @Advice.This(typing = Typing.DYNAMIC) Object thiz, final @Advice.Return Object returned) {
      if (isEnabled(origin))
        PulsarClientAgentIntercept.receiveAsyncEnd(thiz, returned);
    }
  }

  public static class Producer {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, @Advice.This(typing = Typing.DYNAMIC) Object thiz, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object message) {
      if (isEnabled(origin))
        message = PulsarClientAgentIntercept.internalSendAsyncEnter(thiz, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned, final @Advice.Thrown Throwable thrown) {
      if (isEnabled(origin))
        returned = PulsarClientAgentIntercept.internalSendAsyncEnd(returned, thrown);
    }
  }
}