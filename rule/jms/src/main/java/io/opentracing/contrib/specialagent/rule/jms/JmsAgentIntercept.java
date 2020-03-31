/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.jms;

import java.lang.reflect.Method;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;

import io.opentracing.contrib.jms.TracingMessageProducer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import io.opentracing.util.GlobalTracer;

public class JmsAgentIntercept {
  public static Object createProducer(final Object thiz) {
    if (isJms2(thiz))
      return new io.opentracing.contrib.jms2.TracingMessageProducer((MessageProducer) thiz, GlobalTracer.get());
    return new TracingMessageProducer((MessageProducer) thiz, GlobalTracer.get());
  }

  public static Object createConsumer(final Object thiz) {
    return new TracingMessageConsumer((MessageConsumer)thiz, GlobalTracer.get(), true);
  }

  private static boolean isJms2(Object thiz) {
    final Method[] methods = thiz.getClass().getMethods();
    for (Method method : methods) {
      if ("createSharedConsumer".equals(method.getName())) {
        return true;
      }
    }
    return false;
  }
}