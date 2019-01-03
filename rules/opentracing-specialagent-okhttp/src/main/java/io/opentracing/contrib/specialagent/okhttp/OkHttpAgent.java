package io.opentracing.contrib.specialagent.okhttp;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

import io.opentracing.contrib.specialagent.AgentPlugin;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class OkHttpAgent implements AgentPlugin {
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
//    buildAgent(agentArgs).installOn(inst);
  }

  public AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
//      .with(new DebugListener())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(named("okhttp3.OkHttpClient$Builder"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OkHttpAgent.class).on(named("build")));
        }});
  }

  @Advice.OnMethodEnter
  public static void enter(final @Advice.Origin Method method, final @Advice.This Object thiz) {
    System.out.println(">>>>>> " + method);
    Intercept.enter(thiz);
  }
}