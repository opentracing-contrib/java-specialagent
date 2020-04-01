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

package io.opentracing.contrib.specialagent.rule.dynamic;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.ArrayList;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.utility.JavaModule;

public class DynamicAgentRule extends AgentRule {
  private static final String RULES = "sa.integration.dynamic.rules";

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    final ArrayList<AgentBuilder> builders = new ArrayList<>();

    final String rules = System.getProperty(RULES);
    if (rules == null || rules.isEmpty())
      return builders;

    final DynamicSpec[] specs = DynamicSpec.parseRules(rules);
    for (final DynamicSpec spec : specs) {
      Junction<TypeDescription> type = named(spec.className);
      if (spec.polymorphic)
        type = hasSuperType(type);

      builders.add(builder.type(type).transform(new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          Junction<MethodDescription> methodDesc = named(spec.methodName);
          if (spec.args != null) {
            methodDesc = methodDesc.and(takesArguments(spec.args.length));
            for (int i = 0; i < spec.args.length; ++i)
              methodDesc = methodDesc.and(takesArgument(i, named(spec.args[i])));
          }

          if (spec.returning != null) {
            if ("<void>".equals(spec.returning))
              methodDesc = methodDesc.and(returns(void.class));
            else
              methodDesc = methodDesc.and(returns(named(spec.returning)));
          }

          return builder.visit(advice().to(DynamicAgentRule.class).on(methodDesc));
        }
      }));
    }

    return builders;
  }

  @Advice.OnMethodEnter
  public static void enter(final @ClassName String className, final @Advice.Origin String origin) {
    if (isEnabled(className, origin))
      DynamicAgentIntercept.enter(origin);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
    if (isEnabled(className, origin))
      DynamicAgentIntercept.exit(thrown);
  }
}