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

package io.opentracing.contrib.specialagent.mongo;

import com.mongodb.MongoClientSettings.Builder;

import io.opentracing.contrib.mongo.common.TracingCommandListener;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.util.GlobalTracer;

public class MongoDriverAgentIntercept {
  public static void exit(final Object returned) {
    if (!AgentRuleUtil.callerEquals(4, "com.mongodb.async.client.MongoClientSettings.createFromClientSettings"))
      ((Builder)returned).addCommandListener(new TracingCommandListener(GlobalTracer.get()));
  }
}