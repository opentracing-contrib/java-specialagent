/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Properties;

import io.opentracing.contrib.specialagent.AgentPlugin;
import io.opentracing.contrib.specialagent.AgentPluginUtil;
import io.opentracing.contrib.specialagent.EarlyReturnException;
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

public class JdbcAgentPlugin implements AgentPlugin {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs) throws Exception {
    final Narrowable builder = new AgentBuilder.Default()
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(hasSuperType(named("java.sql.Driver")).and(not(named("io.opentracing.contrib.jdbc.TracingDriver"))));

    return Arrays.asList(
      builder.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnEnter.class).on(not(isAbstract()).and(named("connect").and(takesArguments(String.class, Properties.class)))));
        }}),
      builder.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnExit.class).on(not(isAbstract()).and(named("connect").and(takesArguments(String.class, Properties.class)))));
        }})
    );
  }

  public static class OnEnter {
    @Advice.OnMethodEnter
    public static void enter(@Advice.Argument(value = 0) String url, @Advice.Argument(value = 1) Properties info) throws Exception {
      if (!AgentPluginUtil.isEnabled())
        return;

      final Connection connection = JdbcAgentIntercept.enter(url, info);
      if (connection != null)
        throw new EarlyReturnException(connection);
    }
  }

  public static class OnExit {
    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) Throwable thrown) throws Exception {
      if (thrown instanceof EarlyReturnException) {
        returned = ((EarlyReturnException)thrown).getReturnValue();
        thrown = null;
      }
    }
  }
}