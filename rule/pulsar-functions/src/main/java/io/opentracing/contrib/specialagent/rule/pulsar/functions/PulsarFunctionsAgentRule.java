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

package io.opentracing.contrib.specialagent.rule.pulsar.functions;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentracing.contrib.specialagent.AgentRule;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class PulsarFunctionsAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(named("org.apache.pulsar.functions.instance.JavaInstance"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(PulsarFunctionsAgentRule.class).on(named("handleMessage")));
        }}));
  }

  @Advice.OnMethodEnter
  public static void enter(final @Advice.Origin String origin, final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg0,  final @Advice.FieldValue(value = "function") Object function, final @Advice.FieldValue(value = "javaUtilFunction") Object javaUtilFunction, final @Advice.FieldValue(value = "context") Object context) {
    if (isEnabled("PulsarFunctionsAgentRule", origin))
      PulsarFunctionsAgentIntercept.handleMessageEnter(function != null ? function : javaUtilFunction, context, arg0);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(final @Advice.Origin String origin, @Advice.Return(typing = Typing.DYNAMIC) Object returned, final @Advice.Thrown Throwable thrown) {
    if (isEnabled("PulsarFunctionsAgentRule", origin))
       PulsarFunctionsAgentIntercept.handleMessageEnd(returned, thrown);
  }
}