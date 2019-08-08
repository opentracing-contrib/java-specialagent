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

package io.opentracing.contrib.specialagent;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import org.junit.Test;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

/**
 * Test class that validates the early return pattern implemented with
 * ByteBuddy.
 *
 * @author Seva Safris
 */
public class EarlyReturnTest {
  public static void premain(final Instrumentation inst) {
    final Narrowable builder = new AgentBuilder.Default()
      .with(new DebugListener())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(isSubTypeOf(Controller.class));

    builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
         return builder.visit(Advice.to(OnEnter.class).on(named("run")));
      }
    }).installOn(inst);

    builder.transform(new Transformer() {
      @Override
      public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
         return builder.visit(Advice.to(OnExit.class).on(named("run")));
      }
    }).installOn(inst);
  }

  public static class OnEnter {
    @Advice.OnMethodEnter()
    public static void enter() throws IOException {
      System.out.println(">>>>>>>>");
      throw new IOException();
    }
  }

  public static class OnExit {
    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Return(readOnly=false, typing=Typing.DYNAMIC) String returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) ClassNotFoundException thrown) {
      System.out.println("<<<<<<<<");
      returned = "ok!";
      thrown = null;
    }
  }

  public static interface Controller {
    public abstract String run();
  }

  public static class ControllerImpl implements Controller {
    @Override
    public String run() {
      throw new IllegalStateException();
    }
  }

  @Test
  public void test() throws Exception {
    premain(ByteBuddyAgent.install());
    assertEquals("ok!", new ControllerImpl().run());
  }
}