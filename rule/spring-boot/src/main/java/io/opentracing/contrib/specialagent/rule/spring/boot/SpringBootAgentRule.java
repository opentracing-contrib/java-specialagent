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

package io.opentracing.contrib.specialagent.rule.spring.boot;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class SpringBootAgentRule extends AgentRule {
  public static final Logger logger = Logger.getLogger(SpringBootAgentRule.class);
  private static final String[] testClasses = {"org.springframework.boot.loader.Launcher", "org.springframework.boot.SpringApplication"};
  public static boolean initialized;

  @Override
  public boolean isDeferrable(final Instrumentation inst) {
    for (int i = 0; i < testClasses.length; ++i) {
      try {
        Class.forName(testClasses[i], false, ClassLoader.getSystemClassLoader());
        if (logger.isLoggable(Level.FINE))
          logger.fine("\n<<<<<<<<<<<<<<<< Installing SpringBootAgentRule >>>>>>>>>>>>>>>>\n");

        return true;
      }
      catch (final ClassNotFoundException e) {
      }
    }

    return false;
  }

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(hasSuperType(named("org.springframework.boot.SpringApplication")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(SpringBootAgentRule.class).on(named("run").and(isStatic())));
        }}));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(final @Advice.Thrown Throwable thrown) {
    if (initialized)
      return;

    if (thrown != null) {
      logger.log(Level.SEVERE, "Terminating SpecialAgent in liue of application exception:", thrown);
      return;
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("\n<<<<<<<<<<<<<<<< Invoking SpringBootAgentRule >>>>>>>>>>>>>>>>>\n");

    initialized = true;
    AgentRule.initialize();
    if (logger.isLoggable(Level.FINE))
      logger.fine("\n<<<<<<<<<<<<<<<<< Invoked SpringBootAgentRule >>>>>>>>>>>>>>>>>\n");
  }
}