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

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class ThriftProtocolAgentRule extends AgentRule {
  @Override
  public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
    return builder
      .type(hasSuperType(named("org.apache.thrift.protocol.TProtocol")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(WriteMessageBegin.class).on(named("writeMessageBegin")));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(WriteMessageEnd.class).on(named("writeMessageEnd")));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(WriteFieldStop.class).on(named("writeFieldStop")));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(ReadMessageBegin.class).on(named("readMessageBegin")));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(ReadMessageEnd.class).on(named("readMessageEnd")));
        }});
  }

  public static class WriteMessageBegin {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 0) Object message) {
      if (isAllowed(className, origin))
        ThriftProtocolAgentIntercept.writeMessageBegin(thiz, message);
    }
  }

  public static class WriteMessageEnd {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin) {
      if (isAllowed(className, origin))
        ThriftProtocolAgentIntercept.writeMessageEnd();
    }
  }

  public static class WriteFieldStop {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.This Object thiz) throws Exception {
      if (isAllowed(className, origin))
        ThriftProtocolAgentIntercept.writeFieldStop(thiz);
    }
  }

  public static class ReadMessageBegin {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
      if (isAllowed(className, origin) && thrown != null)
        ThriftProtocolAgentIntercept.readMessageBegin(thrown);
    }
  }

  public static class ReadMessageEnd {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin) {
      if (isAllowed(className, origin))
        ThriftProtocolAgentIntercept.readMessageEnd();
    }
  }
}