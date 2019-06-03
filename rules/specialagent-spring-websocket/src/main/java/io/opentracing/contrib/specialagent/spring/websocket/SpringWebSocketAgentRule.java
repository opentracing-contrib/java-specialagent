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

package io.opentracing.contrib.specialagent.spring.websocket;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentracing.contrib.specialagent.AgentRule;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class SpringWebSocketAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
        .type(named("org.springframework.messaging.simp.config.AbstractMessageBrokerConfiguration"))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder
                .visit(Advice.to(InboundChannel.class).on(named("clientInboundChannel")))
                .visit(Advice.to(OutboundChannel.class).on(named("clientOutboundChannel")));
          }
        }),
        builder.type(hasSuperType(named("org.springframework.messaging.simp.stomp.StompSession")))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder
                .visit(Advice.to(StompSessionSend.class).on(named("send").and(takesArguments(2).and(takesArgument(0, named("org.springframework.messaging.simp.stomp.StompHeaders"))))));
          }
        })
    );
  }

  public static class InboundChannel {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, final @Advice.Return(typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled(origin))
        SpringWebSocketAgentIntercept.clientInboundChannel(returned);
    }
  }

  public static class OutboundChannel {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, final @Advice.Return(typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled(origin))
        SpringWebSocketAgentIntercept.clientOutboundChannel(returned);
    }
  }

  public static class StompSessionSend {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin,
        final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg) {
      if (isEnabled(origin))
        SpringWebSocketAgentIntercept.sendEnter(arg);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin,
        final @Advice.Thrown Throwable thrown) {
      if (isEnabled(origin))
        SpringWebSocketAgentIntercept.sendExit(thrown);
    }
  }
}