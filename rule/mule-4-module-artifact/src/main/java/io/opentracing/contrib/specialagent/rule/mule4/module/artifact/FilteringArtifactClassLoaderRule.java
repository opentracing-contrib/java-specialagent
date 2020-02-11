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

package io.opentracing.contrib.specialagent.rule.mule4.module.artifact;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Collections;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Argument;
import net.bytebuddy.asm.Advice.FieldValue;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.asm.Advice.Origin;
import net.bytebuddy.asm.Advice.Return;
import net.bytebuddy.asm.Advice.This;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;

public class FilteringArtifactClassLoaderRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Collections.singletonList(builder
      .type(hasSuperType(named("org.mule.runtime.module.artifact.api.classloader.FilteringArtifactClassLoader")))
      .transform(new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(FilteringArtifactClassLoaderRule.class).on(named("getResource")));
        }
      }));
  }

  @OnMethodExit
  public static void exit(final @Origin String origin, final @This Object thiz, @Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned, final @Argument(value = 0) Object resObj, final @FieldValue(value = "filter") Object filter, final @FieldValue(value = "artifactClassLoader") Object artifactClassLoader) {
    if (isEnabled("FilteringArtifactClassLoaderRule", origin))
      returned = FilteringArtifactAgentIntercept.exit(thiz, returned, resObj, filter, artifactClassLoader);
  }
}