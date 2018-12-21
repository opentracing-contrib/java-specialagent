package io.opentracing.contrib.specialagent.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;

import io.opentracing.contrib.jdbc.TracingConnection;
import io.opentracing.contrib.jdbc.TracingDriver;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class AgentPlugin {
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    buildAgent(agentArgs)
//      .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
      .installOn(inst);
  }

  public static AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
      .type(isSubTypeOf(Driver.class).and(not(is(TracingDriver.class))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(AgentPlugin.class).on(not(isAbstract()).and(named("connect").and(ElementMatchers.returns(Connection.class).and(ElementMatchers.takesArguments(String.class, Properties.class))))));
        }});
  }

  @Advice.OnMethodExit
  @SuppressWarnings("resource")
  public static void exit(@Advice.Origin Method method, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) throws SQLException {
    System.out.println(">>>>>> " + method);
    if (returned == null)
      return;

    final Connection connection = (Connection)returned;
    returned = new TracingConnection(connection,
        connection.getMetaData().getURL().split(":")[1],
        connection.getMetaData().getUserName(),
        true,
        Collections.<String>emptySet(),
        GlobalTracer.get());
  }
}