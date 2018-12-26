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

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
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
 * ByteBuddy agent that intercepts the
 * {@link Agent#findClass(ClassLoader,String)} method to override its returned
 * value.
 * <p>
 * This class is used for testing of {@link ClassLoaderAgent}.
 *
 * @author Seva Safris
 */
public class AgentAgent {
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    new AgentBuilder.Default()
      .ignore(none())
      .disableClassFormatChanges()
//      .with(new DebugListener())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(is(Agent.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AgentAgent.class).on(named("findClass")));
        }}).installOn(inst);
  }

  @Advice.OnMethodExit
  public static void exit(@Advice.Argument(0) ClassLoader classLoader, @Advice.Argument(1) String arg, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) byte[] returned) throws IOException {
    returned = Util.readBytes(ClassLoader.getSystemClassLoader().getResourceAsStream(arg.replace('.', '/').concat(".class")));
    System.err.println("<<<<<<<< Agent#findClass(" + (classLoader == null ? "null" : classLoader.getClass().getName() + "@" + Integer.toString(System.identityHashCode(classLoader), 16)) + "," + arg + "): " + returned);
  }
}