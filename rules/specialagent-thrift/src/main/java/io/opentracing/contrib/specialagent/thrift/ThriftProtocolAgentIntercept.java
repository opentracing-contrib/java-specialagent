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

package io.opentracing.contrib.specialagent.thrift;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TType;

import io.opentracing.Span;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.thrift.ClientSpanDecorator;
import io.opentracing.thrift.DefaultClientSpanDecorator;
import io.opentracing.util.GlobalTracer;

public class ThriftProtocolAgentIntercept {
  private static final short SPAN_FIELD_ID = 3333; // Magic number
  private static final ClientSpanDecorator spanDecorator = new DefaultClientSpanDecorator();
  private static final ThreadLocal<Span> spanHolder = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> oneWay = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };
  private static ThreadLocal<Boolean> injected = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  public static void writeMessageBegin(final Object thiz, final Object message) {
    if (thiz instanceof TProtocolDecorator || ThriftProtocolFactoryAgentIntercept.callerHasClass("org.apache.thrift.protocol.TProtocolDecorator", 5))
      return;

    final TMessage tMessage = (TMessage)message;
    final Span span = GlobalTracer.get().buildSpan(tMessage.name).withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT).start();
    spanHolder.set(span);

    oneWay.set(tMessage.type == TMessageType.ONEWAY);
    injected.set(false);

    spanDecorator.decorate(span, tMessage);
  }

  public static void writeMessageEnd() {
    final Span span = spanHolder.get();
    if (span != null && oneWay.get()) {
      span.finish();
      spanHolder.remove();
      oneWay.remove();
      injected.remove();
    }
  }

  public static void writeFieldStop(final Object protocol) throws TException {
    if (injected.get())
      return;

    final TProtocol tProtocol = (TProtocol)protocol;
    final Span span = spanHolder.get();
    if (span == null)
      return;

    final Map<String,String> map = new HashMap<>();
    GlobalTracer.get().inject(span.context(), Builtin.TEXT_MAP, new TextMapAdapter(map));

    tProtocol.writeFieldBegin(new TField("span", TType.MAP, SPAN_FIELD_ID));
    tProtocol.writeMapBegin(new TMap(TType.STRING, TType.STRING, map.size()));
    for (final Entry<String,String> entry : map.entrySet()) {
      tProtocol.writeString(entry.getKey());
      tProtocol.writeString(entry.getValue());
    }

    tProtocol.writeMapEnd();
    tProtocol.writeFieldEnd();
    injected.set(true);
  }

  public static void readMessageBegin(final Throwable t) {
    final Span span = spanHolder.get();
    if (span == null)
      return;

    spanDecorator.onError(t, span);
    span.finish();
    spanHolder.remove();
    oneWay.remove();
    injected.remove();
  }

  public static void readMessageEnd() {
    final Span span = spanHolder.get();
    if (span == null)
      return;

    span.finish();
    spanHolder.remove();
    oneWay.remove();
    injected.remove();
  }
}