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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

@SuppressWarnings("rawtypes")
public class LettuceMonoDualConsumer<R,T,U extends Throwable> implements Consumer<R>, BiConsumer<T,Throwable> {
  private static final Logger logger = Logger.getLogger(LettuceMonoDualConsumer.class);
  private final RedisCommand command;
  private final boolean finishSpanOnClose;
  private Span span;

  public LettuceMonoDualConsumer(final RedisCommand command, final boolean finishSpanOnClose) {
    this.command = command;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override
  public void accept(final R r) {
    span = GlobalTracer.get().buildSpan(LettuceAgentIntercept.getCommandName(command))
      .withTag(Tags.COMPONENT.getKey(), LettuceAgentIntercept.COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.DB_TYPE.getKey(), LettuceAgentIntercept.DB_TYPE)
      .start();

    if (finishSpanOnClose)
      span.finish();
  }

  @Override
  public void accept(final T t, final Throwable throwable) {
    if (span == null) {
      logger.warning("Failed to finish span, BiConsumer cannot find span because it probably wasn't started");
      return;
    }

    if (throwable != null)
      OpenTracingApiUtil.setErrorTag(span, throwable);

    span.finish();
  }
}