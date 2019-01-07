package io.opentracing.contrib.specialagent.mongo;

import com.mongodb.MongoClientSettings.Builder;

import io.opentracing.contrib.mongo.TracingCommandListener;
import io.opentracing.contrib.specialagent.AgentPluginUtil;
import io.opentracing.util.GlobalTracer;

public class ClientAgentIntercept {
  public static void exit(final Object returned) {
    if (!AgentPluginUtil.callerEquals("com.mongodb.async.client.MongoClientSettings.createFromClientSettings", 4))
      ((Builder)returned).addCommandListener(new TracingCommandListener(GlobalTracer.get()));
  }
}