/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.lettuce;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings({"rawtypes", "unchecked"})
public class LettuceAgentIntercept {
  static final String COMPONENT_NAME = "java-redis";
  static final String DB_TYPE = "redis";

  public static final Set<String> nonInstrumentingCommands = new HashSet<>();

  static {
    final String[] NON_INSTRUMENTING_COMMAND_WORDS = {"SHUTDOWN", "DEBUG", "OOM", "SEGFAULT"};
    Collections.addAll(nonInstrumentingCommands, NON_INSTRUMENTING_COMMAND_WORDS);
  }

  public static void dispatchStart(final Object arg) {
    final RedisCommand command = (RedisCommand)arg;
    final Tracer tracer = GlobalTracer.get();

    final Span span = tracer.buildSpan(getCommandName(command))
      .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.DB_TYPE.getKey(), DB_TYPE)
      .start();

    LocalSpanContext.set(span, tracer.activateSpan(span));
  }

  public static void dispatchEnd(final Object command, final Object returned, final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null || context.decrementAndGet() != 0)
      return;

    if (thrown != null) {
      OpenTracingApiUtil.setErrorTag(context.getSpan(), thrown);
      context.closeAndFinish();
      return;
    }

    context.closeScope();

    final Span span = context.getSpan();
    if (doFinishSpanEarly((RedisCommand)command)) {
      span.finish();
    }
    else {
      ((AsyncCommand<?,?,?>)returned).handleAsync(new BiFunction<Object,Throwable,Object>() {
        @Override
        public Object apply(final Object o, final Throwable throwable) {
          if (throwable != null)
            OpenTracingApiUtil.setErrorTag(span, throwable);

          span.finish();
          return null;
        }
      });
    }

    context.closeScope();
  }

  public static String getCommandName(final RedisCommand command) {
    if (command != null && command.getType() != null)
      return command.getType().name().trim();

    return "Redis Command";
  }

  public static boolean doFinishSpanEarly(final RedisCommand command) {
    final String commandName = getCommandName(command);
    return nonInstrumentingCommands.contains(commandName);
  }

  public static Object createMonoEnd(final Object supplier, final Object returned) {
    final RedisCommand command = ((Supplier<RedisCommand>)supplier).get();

    final boolean finishSpanOnClose = doFinishSpanEarly(command);
    final LettuceMonoDualConsumer lettuceMonoDualConsumer = new LettuceMonoDualConsumer(command, finishSpanOnClose);

    final Mono<?> publisher = ((Mono<?>)returned).doOnSubscribe(lettuceMonoDualConsumer);
    // register the call back to close the span only if necessary
    return finishSpanOnClose ? publisher : publisher.doOnSuccessOrError(lettuceMonoDualConsumer);
  }

  public static Object createFluxEnd(final Object supplier, final Object returned) {
    final RedisCommand command = ((Supplier<RedisCommand>)supplier).get();
    final boolean finishSpanOnClose = doFinishSpanEarly(command);

    final LettuceFluxTerminationRunnable handler = new LettuceFluxTerminationRunnable(command, finishSpanOnClose);
    final Flux<?> publisher = ((Flux<?>)returned).doOnSubscribe(handler.getOnSubscribeConsumer());

    return finishSpanOnClose ? publisher : publisher.doOnEach(handler).doOnCancel(handler);
  }

  public static void connectStart(Object arg) {
    final RedisURI redisURI = (RedisURI) arg;
    final Tracer tracer = GlobalTracer.get();

    final Span span = tracer.buildSpan("CONNECT")
      .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.DB_TYPE.getKey(), DB_TYPE)
      .withTag(Tags.PEER_HOSTNAME, redisURI.getHost())
      .withTag(Tags.PEER_PORT, redisURI.getPort())
      .withTag("db.redis.dbIndex", redisURI.getDatabase())
      .start();

    LocalSpanContext.set(span, tracer.activateSpan(span));
  }

  public static void connectEnd(final Object returned, final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null || context.decrementAndGet() != 0)
      return;

    if (thrown != null) {
      OpenTracingApiUtil.setErrorTag(context.getSpan(), thrown);
      context.closeAndFinish();
      return;
    }

    final Span span = context.getSpan();
    ((ConnectionFuture<?>)returned).handleAsync(new BiFunction<Object,Throwable,Object>() {
      @Override
      public Object apply(final Object o, final Throwable throwable) {
        if (throwable != null)
          OpenTracingApiUtil.setErrorTag(span, throwable);

        span.finish();
        return null;
      }
    });

    context.closeScope();
  }
}