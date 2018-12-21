package io.opentracing.contrib.agent.concurrent;

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

public class OkHttpInterceptor {
  public static void premain(final String arg, final Instrumentation inst) throws Exception {
    new AgentBuilder.Default()
      .type(named("okhttp3.OkHttpClient$Builder"))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OkHttpInterceptor.class).on(named("build")));
        }})
      .installOn(inst);
  }

  @Advice.OnMethodEnter
  public static void enter(@Advice.Origin Method method, @Advice.This Object thiz) {
    System.out.println(">>>>>> " + method);
    final TracingInterceptor interceptor = new TracingInterceptor(GlobalTracer.get(), Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    final OkHttpClient.Builder builder = (OkHttpClient.Builder)thiz;
    builder.addInterceptor(interceptor);
    builder.addNetworkInterceptor(interceptor);
  }
}