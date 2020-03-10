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

package io.opentracing.contrib.specialagent.rule.thrift;

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

public class ThriftAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(not(isInterface()).and(hasSuperType(named("org.apache.thrift.async.AsyncMethodCallback"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AsyncMethodCallback.OnComplete.class).on(named("onComplete")));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AsyncMethodCallback.OnError.class).on(named("onError")));
        }})
      .type(named("org.apache.thrift.TProcessorFactory"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(Processor.class).on(named("getProcessor")));
        }})
      .type(hasSuperType(named("org.apache.thrift.protocol.TProtocolFactory")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(ProtocolFactory.class).on(named("getProtocol")));
        }}));
  }

  public static class AsyncMethodCallback {
    public static class OnComplete {
      @Advice.OnMethodExit
      public static void exit(final @Advice.Origin String origin) {
        if (isEnabled("ThriftAgentRule", origin))
          ThriftAgentIntercept.onComplete();
      }
    }

    public static class OnError {
      @Advice.OnMethodExit
      public static void exit(final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object exception) {
        if (isEnabled("ThriftAgentRule", origin))
          ThriftAgentIntercept.onError(exception);
      }
    }
  }

  public static class Processor {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled("ThriftAgentRule", origin))
        returned = ThriftAgentIntercept.getProcessor(returned);
    }
  }

  public static class ProtocolFactory {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
      if (isEnabled("ThriftAgentRule", origin))
        returned = ThriftProtocolFactoryAgentIntercept.exit(returned);
    }
  }
}