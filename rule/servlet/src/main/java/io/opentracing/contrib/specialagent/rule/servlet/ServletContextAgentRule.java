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

package io.opentracing.contrib.specialagent.rule.servlet;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class ServletContextAgentRule extends AgentRule {
  public static boolean filterAdded = false;

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(named("org.eclipse.jetty.servlet.ServletContextHandler"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(JettyAdvice.class).on(isConstructor()));
        }})
      .type(not(isInterface()).and(hasSuperType(named("javax.servlet.ServletContext"))
        // Jetty is handled separately due to the (otherwise) need for tracking state of the ServletContext
        .and(not(nameStartsWith("org.eclipse.jetty")))
        // Similarly, ApplicationContextFacade causes trouble and it's enough to instrument ApplicationContext
        .and(not(named("org.apache.catalina.core.ApplicationContextFacade")))
        // Otherwise we are breaking Tomcat 8.5+
        .and(not(named("org.apache.catalina.core.StandardContext$NoPluggabilityServletContext")))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(ServletContextAdvice.class).on(isConstructor()));
        }}));
  }

  public static class JettyAdvice {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, final @Advice.This Object thiz) {
      if (isEnabled(origin))
        filterAdded = JettyAgentIntercept.addFilter(thiz);
    }
  }

  public static class ServletContextAdvice {
    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin, final @Advice.This Object thiz) {
      if (isEnabled(origin))
        filterAdded = ServletContextAgentIntercept.addFilter(thiz);
    }
  }
}