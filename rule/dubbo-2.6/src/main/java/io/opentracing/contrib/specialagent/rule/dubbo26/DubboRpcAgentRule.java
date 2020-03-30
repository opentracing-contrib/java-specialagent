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

package io.opentracing.contrib.specialagent.rule.dubbo26;

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

public class DubboRpcAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(named("com.alibaba.dubbo.common.extension.ExtensionLoader"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(DubboRpcAgentRule.class).on(named("getActivateExtension")));
        }}));
  }

    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, final @Advice.Argument(value = 1) Object  key,
                            @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) {
        if (key instanceof String) {
            if ("service.filter".equals(key)) {
                if (isEnabled("DubboRpcAgentRule", origin)) {
                    returned = DubboAgentIntercept.exit(returned);
                }
            } else if ("reference.filter".equals(key)) {
                if (isEnabled("DubboRpcAgentRule", origin)) {
                    returned = DubboAgentIntercept.exit(returned);
                }
            }
        }
    }

}