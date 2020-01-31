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

package io.opentracing.contrib.specialagent.rule.akka.http;

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

public class AkkaHttpServerAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(hasSuperType(named("akka.http.javadsl.Http")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(SyncHandler.class).on(named("bindAndHandleSync").and(takesArgument(0, named("akka.japi.Function")))));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AsyncHandler.class).on(named("bindAndHandleAsync").and(takesArgument(0, named("akka.japi.Function")))));
        }}));
  }

  public static class SyncHandler {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object arg0) {
      if (isEnabled(AkkaHttpServerAgentRule.class, origin))
        arg0 = AkkaAgentIntercept.bindAndHandleSync(arg0);
    }
  }

  public static class AsyncHandler {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, @Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object arg0) {
      if (isEnabled(AkkaHttpServerAgentRule.class, origin))
        arg0 = AkkaAgentIntercept.bindAndHandleAsync(arg0);
    }
  }
}