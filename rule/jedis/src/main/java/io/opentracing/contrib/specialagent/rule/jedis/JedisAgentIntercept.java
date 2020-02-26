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

package io.opentracing.contrib.specialagent.rule.jedis;

import io.opentracing.contrib.specialagent.SpanUtil;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import redis.clients.jedis.Protocol.Command;

public class JedisAgentIntercept {
  private static final ThreadLocal<Queue<Span>> spanHolder = new ThreadLocal<Queue<Span>>() {
    @Override
    protected Queue<Span> initialValue() {
      return new LinkedList<>();
    }
  };

  public static void sendCommand(final Object command, final byte[][] args) {
    final Command cmd = (Command)command;
    final Span span = GlobalTracer.get()
      .buildSpan(cmd.name())
      .withTag(Tags.COMPONENT.getKey(), "java-redis")
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.DB_TYPE.getKey(), "redis").start();

    final String redisCommand = convert(args);
    if (redisCommand != null)
      span.setTag(Tags.DB_STATEMENT, redisCommand);

    spanHolder.get().add(span);
  }

  private static String convert(final byte[][] args) {
    if (args == null || args.length == 0)
      return null;

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < args.length; ++i) {
      if (i > 0)
        builder.append(' ');

      builder.append(new String(args[i], StandardCharsets.UTF_8));
    }

    return builder.toString();
  }

  public static void readCommandOutput() {
    final Queue<Span> spans = spanHolder.get();
    if (spans.isEmpty())
      return;

    final Span span = spans.poll();
    span.finish();
  }

  public static void onError(final Throwable throwable) {
    final Queue<Span> spans = spanHolder.get();
    if (spans.isEmpty())
      return;

    final Span span = spans.poll();
    if (throwable != null) {
      SpanUtil.onError(throwable, span);
    }

    span.finish();
  }

}