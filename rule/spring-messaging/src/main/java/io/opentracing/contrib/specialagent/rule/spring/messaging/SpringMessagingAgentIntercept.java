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

package io.opentracing.contrib.specialagent.rule.spring.messaging;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.messaging.support.ChannelInterceptor;

import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.specialagent.rule.spring.messaging.copied.OpenTracingChannelInterceptor;
import io.opentracing.util.GlobalTracer;

public class SpringMessagingAgentIntercept {
  public static final Logger logger = Logger.getLogger(SpringMessagingAgentIntercept.class);

  @SuppressWarnings("unchecked")
  public static void enter(final Object thiz) {
    try {
      // Reflection is used because
      // org.springframework.integration.channel.AbstractMessageChannel$ChannelInterceptorList
      // is protected static class
      final Method getInterceptors = thiz.getClass().getMethod("getInterceptors");
      final List<ChannelInterceptor> interceptors = (List<ChannelInterceptor>)getInterceptors.invoke(thiz);

      for (final ChannelInterceptor interceptor : interceptors)
        if (interceptor instanceof OpenTracingChannelInterceptor)
          return;

      final Method addInterceptor = thiz.getClass().getMethod("add", ChannelInterceptor.class);
      addInterceptor.invoke(thiz, new OpenTracingChannelInterceptor(GlobalTracer.get()));
    }
    catch (final Exception e) {
      logger.log(Level.FINE, e.getMessage(), e);
    }
  }
}