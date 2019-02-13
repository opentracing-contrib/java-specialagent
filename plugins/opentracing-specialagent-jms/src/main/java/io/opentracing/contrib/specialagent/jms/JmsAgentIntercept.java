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

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;

import io.opentracing.contrib.jms2.TracingMessageProducer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;

public class JmsAgentIntercept {
  public static Object createProducer(final Object thiz) {
    return thiz instanceof TracingMessageProducer ? thiz : new TracingMessageProducer((MessageProducer)thiz);
  }

  public static Object createConsumer(final Object thiz) {
    return thiz instanceof TracingMessageConsumer ? thiz : new TracingMessageConsumer((MessageConsumer)thiz);
  }
}