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

package io.opentracing.contrib.specialagent.rule.dubbo27;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.support.RpcUtils;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class DubboFilter implements Filter {
  @Override
  public Result invoke(final Invoker<?> invoker, final Invocation invocation) throws RpcException {
    if (!GlobalTracer.isRegistered())
      return invoker.invoke(invocation);

    final RpcContext rpcContext = RpcContext.getContext();
    final Tracer tracer = GlobalTracer.get();
    final String service = invoker.getInterface().getSimpleName();
    final String name = service + "/" + RpcUtils.getMethodName(invocation);
    final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(name);
    final Span span;
    if (rpcContext.isProviderSide()) {
      spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
      spanBuilder.withTag(Tags.COMPONENT.getKey(), "java-dubbo");
      final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new DubboFilter.DubboAdapter(rpcContext));
      if (spanContext != null)
        spanBuilder.asChildOf(spanContext);

      span = spanBuilder.start();
    }
    else {
      spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
      spanBuilder.withTag(Tags.COMPONENT.getKey(), "java-dubbo");
      span = spanBuilder.start();
      tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new DubboFilter.DubboAdapter(rpcContext));
    }

    final InetSocketAddress remoteAddress = rpcContext.getRemoteAddress();
    if (remoteAddress != null)
      span.setTag("remoteAddress", remoteAddress.getHostString() + ":" + remoteAddress.getPort());

    try (final Scope scope = tracer.scopeManager().activate(span)) {
      final Result result = invoker.invoke(invocation);
      if (result.hasException()) {
        final Throwable t = result.getException();
        errorLogs(t, span);
        if (t instanceof RpcException)
          span.setTag("dubbo.error_code", Integer.toString(((RpcException)t).getCode()));
      }

      return result;
    }
    catch (final Throwable t) {
      errorLogs(t, span);
      if (t instanceof RpcException)
        span.setTag("dubbo.error_code", Integer.toString(((RpcException)t).getCode()));

      throw t;
    }
    finally {
      span.finish();
    }
  }

  class DubboAdapter implements TextMap {
    private final RpcContext rpcContext;

    public DubboAdapter(final RpcContext rpcContext) {
      this.rpcContext = rpcContext;
    }

    @Override
    public Iterator<Map.Entry<String,String>> iterator() {
      return this.rpcContext.getAttachments().entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
      rpcContext.getAttachments().put(key, value);
    }
  }

  private static void errorLogs(final Throwable throwable, final Span span) {
    final Map<String,Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", throwable);
    errorLogs.put("error.kind", throwable.getClass().getName());
    final String message = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
    if (message != null)
      errorLogs.put("message", message + "");

    final StringWriter stringWriter = new StringWriter();
    throwable.printStackTrace(new PrintWriter(stringWriter));
    errorLogs.put("stack", stringWriter.toString());
    Tags.ERROR.set(span, true);
    span.log(errorLogs);
  }
}