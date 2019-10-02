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

package io.opentracing.contrib.specialagent.kafka.spring;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;
import java.util.logging.Logger;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class SpringKafkaAgentRule extends AgentRule {
  private static final Logger logger = Logger.getLogger(SpringKafkaAgentRule.class.getName());

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(not(isInterface()).and(hasSuperType(named("org.springframework.kafka.listener.MessageListener"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(SpringKafkaAgentRule.class).on(named("onMessage")));
        }}));
  }

  @Advice.OnMethodEnter
  public static void enter(final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object record) {
    if (isEnabled(origin))
      SpringKafkaAgentIntercept.onMessageEnter(record);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
    if (isEnabled(origin))
      SpringKafkaAgentIntercept.onMessageExit(thrown);
  }
}