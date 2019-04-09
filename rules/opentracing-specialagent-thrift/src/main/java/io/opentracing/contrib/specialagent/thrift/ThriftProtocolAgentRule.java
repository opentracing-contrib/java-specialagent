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

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class ThriftProtocolAgentRule implements AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs) {
    final Narrowable builder = new AgentBuilder.Default()
        .ignore(none())
        .with(RedefinitionStrategy.RETRANSFORMATION)
        .with(InitializationStrategy.NoOp.INSTANCE)
        .with(TypeStrategy.Default.REDEFINE)
        .type(hasSuperType(named("org.apache.thrift.protocol.TProtocol")));

    return Arrays.asList(builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(WriteMessageBegin.class).on(named("writeMessageBegin")));
      }
    }), builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(WriteMessageEnd.class).on(named("writeMessageEnd")));
      }
    }), builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(WriteFieldStop.class).on(named("writeFieldStop")));
      }
    }), builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(ReadMessageBegin.class).on(named("readMessageBegin")));
      }
    }), builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription,
          final ClassLoader classLoader, final JavaModule module) {
        return builder.visit(Advice.to(ReadMessageEnd.class).on(named("readMessageEnd")));
      }
    }));
  }

  public static class WriteMessageBegin {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.This Object thiz,
        @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object message) {
      if (AgentRuleUtil.isEnabled()) {
        ThriftProtocolAgentIntercept.writeMessageBegin(thiz, message);
      }
    }
  }

  public static class WriteMessageEnd {
    @Advice.OnMethodExit
    public static void exit() {
      if (AgentRuleUtil.isEnabled()) {
        ThriftProtocolAgentIntercept.writeMessageEnd();
      }
    }
  }

  public static class WriteFieldStop {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.This Object thiz)
        throws Exception {
      if (AgentRuleUtil.isEnabled()) {
        ThriftProtocolAgentIntercept.writeFieldStop(thiz);
      }
    }
  }

  public static class ReadMessageBegin {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Thrown(typing = Typing.DYNAMIC) Throwable thrown) {
      if (AgentRuleUtil.isEnabled() && thrown != null) {
        ThriftProtocolAgentIntercept.readMessageBegin(thrown);
      }
    }
  }

  public static class ReadMessageEnd {
    @Advice.OnMethodExit
    public static void exit() {
      if (AgentRuleUtil.isEnabled()) {
        ThriftProtocolAgentIntercept.readMessageEnd();
      }
    }
  }


}