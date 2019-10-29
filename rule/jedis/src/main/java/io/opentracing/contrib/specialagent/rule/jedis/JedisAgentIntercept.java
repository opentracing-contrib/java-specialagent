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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import redis.clients.jedis.Protocol.Command;

public class JedisAgentIntercept {
  private final static ThreadLocal<Queue<Span>> spanHolder = ThreadLocal.withInitial(LinkedList::new);

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

    final List<String> commands = new ArrayList<>();
    for (final byte[] arg : args)
      commands.add(new String(arg, StandardCharsets.UTF_8));

    return String.join(" ", commands);
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
    Tags.ERROR.set(span, Boolean.TRUE);
    if (throwable != null)
      span.log(errorLogs(throwable));

    span.finish();
  }

  private static Map<String,Object> errorLogs(final Throwable throwable) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", throwable);
    return errorLogs;
  }
}