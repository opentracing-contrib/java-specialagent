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

package io.opentracing.contrib.specialagent.rule.akka.actor;

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

public class AkkaAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(hasSuperType(named("akka.actor.AbstractActor")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(Receive.class).on(named("aroundReceive").and(takesArguments(2))));
        }})
      .type(named("akka.pattern.AskSupport"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule javaModule) {
          return  builder.visit(Advice.to(Ask.class).on(named("ask").and(takesArguments(2).or(takesArguments(3).or(takesArguments(4))))));
        }})
      .type(hasSuperType(named("akka.actor.ActorRef")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule javaModule) {
          return  builder.visit(Advice.to(Tell.class).on(named("tell").and(takesArguments(2))));
      }})
    );
  }

  public static class Receive {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz, @Advice.Argument(value = 1, readOnly = false, typing = Typing.DYNAMIC) Object message) {
      if (isEnabled(origin))
       message = AkkaAgentIntercept.aroundReceiveStart(thiz, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
      if (isEnabled(origin))
        AkkaAgentIntercept.aroundReceiveEnd(thrown);
    }
  }

  public static class Tell {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object message, final @Advice.Argument(value = 1) Object sender) {
      if (isEnabled(origin))
        message = AkkaAgentIntercept.askStart(thiz, message, "tell", sender);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 0) Object message, final @Advice.Argument(value = 1) Object sender, final @Advice.Thrown Throwable thrown) {
      if (isEnabled(origin))
        AkkaAgentIntercept.askEnd(thiz, message, thrown, sender);
    }
  }

  public static class Ask {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object actorRef, @Advice.Argument(value = 1, readOnly = false, typing = Typing.DYNAMIC) Object message) {
      if (isEnabled(origin))
        message = AkkaAgentIntercept.askStart(actorRef, message, "ask", null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object actorRef, final @Advice.Argument(value = 1) Object message, final @Advice.Thrown Throwable thrown) {
      if (isEnabled(origin))
        AkkaAgentIntercept.askEnd(actorRef, message, thrown, null);
    }
  }
}