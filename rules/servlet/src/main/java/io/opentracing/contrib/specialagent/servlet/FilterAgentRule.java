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

package io.opentracing.contrib.specialagent.servlet;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.EarlyReturnException;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class FilterAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(hasSuperType(named("javax.servlet.http.HttpServlet")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder
            .visit(Advice.to(ServletInitAdvice.class).on(named("init").and(takesArguments(1)).and(takesArgument(0, named("javax.servlet.ServletConfig")))))
            .visit(Advice.to(ServletServiceAdvice.class).on(named("service").and(takesArguments(2)).and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")).and(takesArgument(1, named("javax.servlet.http.HttpServletResponse"))))));
        }})
      .type(not(isInterface()).and(hasSuperType(named("javax.servlet.http.HttpServletResponse"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder
            .visit(Advice.to(HttpServletResponseAdvice.class).on(named("setStatus")))
            .visit(Advice.to(HttpServletResponseAdvice.class).on(named("sendError")));
        }})
      .type(not(isInterface()).and(hasSuperType(named("javax.servlet.Filter")).and(not(named("io.opentracing.contrib.web.servlet.filter.TracingFilter")))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder
            .visit(Advice.to(FilterInitAdvice.class).on(named("init")))
            .visit(Advice.to(DoFilterEnter.class).on(named("doFilter")));
        }}),
      builder
      .type(not(isInterface()).and(hasSuperType(named("javax.servlet.Filter")).and(not(named("io.opentracing.contrib.web.servlet.filter.TracingFilter")))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(DoFilterExit.class).on(named("doFilter")));
        }}));
  }

  public static class ServletInitAdvice {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 0) Object servletConfig) {
      if (isEnabled(origin))
        ServletAgentIntercept.init(thiz, servletConfig);
    }
  }

  public static class ServletServiceAdvice {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 0) Object request, final @Advice.Argument(value = 1) Object response) {
      if (isEnabled(origin))
        ServletAgentIntercept.service(thiz, request, response);
    }
  }

  public static class HttpServletResponseAdvice {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 0) int status) {
      if (isEnabled(origin))
        FilterAgentIntercept.setStatusCode(thiz, status);
    }
  }

  public static class FilterInitAdvice {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 0) Object filterConfig) {
      if (isEnabled(origin))
        FilterAgentIntercept.init(thiz, filterConfig);
    }
  }

  public static class DoFilterEnter {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 0) Object request, final @Advice.Argument(value = 1) Object response, final @Advice.Argument(value = 2) Object chain) {
      if (!ServletContextAgentRule.filterAdded && isEnabled(origin))
        FilterAgentIntercept.doFilter(thiz, request, response, chain);
    }
  }

  public static class DoFilterExit {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) Throwable thrown) {
      if (thrown instanceof EarlyReturnException)
        thrown = null;
    }
  }
}