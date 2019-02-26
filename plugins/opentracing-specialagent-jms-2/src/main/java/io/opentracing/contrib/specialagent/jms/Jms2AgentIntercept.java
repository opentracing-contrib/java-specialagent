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
package io.opentracing.contrib.specialagent.jms;

import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import io.opentracing.contrib.jms2.TracingMessageProducer;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;

public class Jms2AgentIntercept {
  private static Boolean isJms2;

  private static boolean isJms2() {
    if(isJms2 != null) {
      return isJms2;
    }
    try {
      Class.forName("javax.jms.JMSContext");
      isJms2 = true;
    } catch (ClassNotFoundException ignore) {
      isJms2 = false;
    }
    return isJms2;
  }

  public static Object createProducer(final Object thiz) {
    if(!isJms2()) {
      return thiz;
    }
    return thiz instanceof TracingMessageProducer ? thiz : new TracingMessageProducer((MessageProducer)thiz);
  }

  public static Object createConsumer(final Object thiz) {
    if(!isJms2()) {
      return thiz;
    }
    return thiz instanceof TracingMessageConsumer ? thiz : new TracingMessageConsumer((MessageConsumer)thiz);
  }
}