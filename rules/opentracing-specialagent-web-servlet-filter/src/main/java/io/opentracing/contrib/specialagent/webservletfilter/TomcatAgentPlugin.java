package io.opentracing.contrib.specialagent.webservletfilter;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.reflect.Constructor;

import io.opentracing.contrib.specialagent.AgentPlugin;
import io.opentracing.contrib.specialagent.DebugListener;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class TomcatAgentPlugin implements AgentPlugin {
  @Override
  public AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
      .with(new DebugListener())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(named("org.apache.catalina.core.ApplicationContext"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(TomcatAgentPlugin.class).on(isConstructor()));
        }});
  }

  @Advice.OnMethodExit
  public static void exit(final @Advice.Origin Constructor<?> constructor, final @Advice.This Object thiz) {
    System.out.println(">>>>>> " + constructor);
    TomcatAgentIntercept.exit(thiz);
  }
}