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

package io.opentracing.contrib.specialagent.rule.kafka.streams;

import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class KafkaStreamsAgentRule extends AgentRule {
  @Override
  public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
    return builder
      .type(named("org.apache.kafka.streams.processor.internals.PartitionGroup"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(NextRecord.class).on(named("nextRecord")));
        }})
      .type(named("org.apache.kafka.streams.processor.internals.StreamTask"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(Process.class).on(named("process")));
        }})
      .type(named("org.apache.kafka.streams.processor.internals.RecordDeserializer"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(Deserialize.class).on(named("deserialize")));
        }});
  }

  public static class NextRecord {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Return Object returned) {
      if (isAllowed(className, origin))
        KafkaStreamsAgentIntercept.onNextRecordExit(returned);
    }
  }

  public static class Process {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
      if (isAllowed(className, origin))
         KafkaStreamsAgentIntercept.onProcessExit(thrown);
    }
  }

  public static class Deserialize {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Return Object returned, final @Advice.Argument(value = 1) Object record) {
      if (isAllowed(className, origin))
        KafkaStreamsAgentIntercept.onDeserializeExit(returned, record);
    }
  }
}