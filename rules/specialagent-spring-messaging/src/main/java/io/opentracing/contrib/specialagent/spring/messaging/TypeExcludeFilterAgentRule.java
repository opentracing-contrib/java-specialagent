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

package io.opentracing.contrib.specialagent.spring.messaging;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class TypeExcludeFilterAgentRule extends AgentRule {
  public static final Logger logger = Logger.getLogger(TypeExcludeFilterAgentRule.class);

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(hasSuperType(named("org.springframework.boot.context.TypeExcludeFilter")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(TypeExcludeFilter.class).on(named("match").and(takesArguments(2))));
        }}), builder
      .type(not(isInterface()).and(hasSuperType(named("org.springframework.beans.factory.ObjectProvider"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(DefaultListableBeanFactory.class).on(named("getIfAvailable").and(takesArguments(0))));
        }}));
  }

  public static class TypeExcludeFilter {
    @Advice.OnMethodExit(onThrowable = NoClassDefFoundError.class)
    public static void exit(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) NoClassDefFoundError thrown) {
      if (isEnabled(origin)) {
        logger.log(Level.INFO, thrown.getMessage(), thrown);
        thrown = null;
        returned = Boolean.FALSE;
      }
    }
  }

  public static class DefaultListableBeanFactory {
    @Advice.OnMethodExit(onThrowable = NoClassDefFoundError.class)
    public static void exit(final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) NoClassDefFoundError thrown) {
      if (isEnabled(origin)) {
        logger.log(Level.INFO, thrown.getMessage(), thrown);
        thrown = null;
        returned = null;
      }
    }
  }
}