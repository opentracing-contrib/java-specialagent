package io.opentracing.contrib.specialagent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class ClassLoaderAgentTest {
  static {
    try {
      final Instrumentation instrumentation = ByteBuddyAgent.install();
      ClassLoaderAgent.premain(null, instrumentation);
      premain(null, instrumentation);
    }
    catch (final Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    buildAgent(agentArgs)
//      .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
      .installOn(inst);
  }

  public static AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(is(Agent.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(ClassLoaderAgentTest.class).on(named("findClass").and(isStatic())));
        }});
  }

  @Advice.OnMethodEnter
  public static void exit() throws IOException {
    System.out.println(">>>>>> ");
//    returned = Util.readBytes(ClassLoader.getSystemClassLoader().getResourceAsStream(arg.replace('.', '/').concat(".class")));
  }

  @Test
  public void test() throws Exception {
    final URLClassLoader classLoader = new URLClassLoader(new URL[] {}, null);
    Class.forName(ClassLoaderAgentTest.class.getName(), true, classLoader);
  }
}