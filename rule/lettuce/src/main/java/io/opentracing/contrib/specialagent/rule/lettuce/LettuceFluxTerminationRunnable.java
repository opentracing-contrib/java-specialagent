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

import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Span;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.function.Consumer;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

@SuppressWarnings("rawtypes")
public class LettuceFluxTerminationRunnable implements Consumer<Signal>, Runnable {
  private static final Logger logger = Logger.getLogger(LettuceFluxTerminationRunnable.class);
  private Span span = null;
  private int numResults = 0;
  private final FluxOnSubscribeConsumer onSubscribeConsumer;

  public LettuceFluxTerminationRunnable(final RedisCommand command, final boolean finishSpanOnClose) {
    onSubscribeConsumer = new FluxOnSubscribeConsumer(this, command, finishSpanOnClose);
  }

  public FluxOnSubscribeConsumer getOnSubscribeConsumer() {
    return onSubscribeConsumer;
  }

  private void finishSpan(final boolean isCommandCancelled, final Throwable throwable) {
    if (span != null) {
      span.setTag("db.command.results.count", numResults);
      if (isCommandCancelled) {
        span.setTag("db.command.cancelled", true);
      }
      if (throwable != null)
        OpenTracingApiUtil.setErrorTag(span, throwable);
      span.finish();
    } else {
      logger.warning("Failed to finish span, LettuceFluxTerminationRunnable cannot find span because it probably wasn't started.");
    }
  }

  @Override
  public void accept(final Signal signal) {
    if (SignalType.ON_COMPLETE.equals(signal.getType()) || SignalType.ON_ERROR.equals(signal.getType())) {
      finishSpan(false, signal.getThrowable());
    } else if (SignalType.ON_NEXT.equals(signal.getType())) {
      ++numResults;
    }
  }

  @Override
  public void run() {
    if (span != null) {
      finishSpan(true, null);
    } else {
      logger.warning("Failed to finish span to indicate cancellation, LettuceFluxTerminationRunnable cannot find this.span because it probably wasn't started.");
    }
  }

  public static class FluxOnSubscribeConsumer implements Consumer<Subscription> {
    private final LettuceFluxTerminationRunnable owner;
    private final RedisCommand command;
    private final boolean finishSpanOnClose;

    public FluxOnSubscribeConsumer(final LettuceFluxTerminationRunnable owner, final RedisCommand command, final boolean finishSpanOnClose) {
      this.owner = owner;
      this.command = command;
      this.finishSpanOnClose = finishSpanOnClose;
    }

    @Override
    public void accept(final Subscription subscription) {
      final Span span = GlobalTracer.get().buildSpan(LettuceAgentIntercept.getCommandName(command))
          .withTag(Tags.COMPONENT.getKey(), LettuceAgentIntercept.COMPONENT_NAME)
          .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
          .withTag(Tags.DB_TYPE.getKey(), LettuceAgentIntercept.DB_TYPE)
          .start();
      owner.span = span;
      if (finishSpanOnClose) {
        span.finish();
      }
    }
  }
}
