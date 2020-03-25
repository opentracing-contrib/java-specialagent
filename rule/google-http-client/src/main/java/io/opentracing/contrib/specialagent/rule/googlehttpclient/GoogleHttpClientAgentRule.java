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

package io.opentracing.contrib.specialagent.rule.googlehttpclient;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class GoogleHttpClientAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) {
    return Arrays.asList(builder
      .type(hasSuperType(named("com.google.api.client.http.HttpRequest")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(GoogleHttpClientAgentRule.class).on(named("execute").and(takesArguments(0))));
        }}));
  }

  @Advice.OnMethodEnter
  public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz) {
    if (isEnabled(GoogleHttpClientAgentRule.class.getName(), origin))
      GoogleHttpClientAgentIntercept.enter(thiz);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown, @Advice.Return final Object returned) {
    if (isEnabled(GoogleHttpClientAgentRule.class.getName(), origin))
      GoogleHttpClientAgentIntercept.exit(thrown, returned);
  }
}