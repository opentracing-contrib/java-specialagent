package io.opentracing.contrib.specialagent.okhttp;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Collections;

import io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator;
import io.opentracing.contrib.okhttp3.TracingInterceptor;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;
import okhttp3.OkHttpClient;

public class AgentPlugin {
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    buildAgent(agentArgs)
//      .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
      .installOn(inst);
  }

  public static AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
      .type(is(OkHttpClient.Builder.class))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AgentPlugin.class).on(named("build")));
        }});
  }

  @Advice.OnMethodEnter
  public static void enter(@Advice.Origin Method method, @Advice.This OkHttpClient.Builder thiz) {
    System.out.println(">>>>>> " + method);
    final TracingInterceptor interceptor = new TracingInterceptor(GlobalTracer.get(), Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    thiz.addInterceptor(interceptor);
    thiz.addNetworkInterceptor(interceptor);
  }
}