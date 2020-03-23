package io.opentracing.contrib.specialagent.rule.dubbo26;


import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DubboRpcFilter implements Filter{

    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {

        if (!GlobalTracer.isRegistered()) {
            return invoker.invoke(invocation);
        }
        RpcContext rpcContext = RpcContext.getContext();
        Tracer tracer = GlobalTracer.get();
        String service = invoker.getInterface().getSimpleName();
        String name = service + "/" + RpcUtils.getMethodName(invocation);
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(name);
        Span span = null;
        if (rpcContext.isProviderSide()) {
            spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
            spanBuilder.withTag(Tags.COMPONENT.getKey(), "java-dubbo");
            SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new DubboAdapter(rpcContext));
            if (spanContext != null) {
                spanBuilder.asChildOf(spanContext);
            }
            span = spanBuilder.start();
        } else {
            spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
            spanBuilder.withTag(Tags.COMPONENT.getKey(), "java-dubbo");
            span = spanBuilder.start();
            tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new DubboAdapter(rpcContext));
        }

        InetSocketAddress remoteAddress = rpcContext.getRemoteAddress();
        if (remoteAddress != null) {
            span.setTag("remoteAddress", remoteAddress.getHostString() + ":" + remoteAddress.getPort());
        }

        try (Scope scope = tracer.scopeManager().activate(span)) {
            Result result = invoker.invoke(invocation);
            if (result.hasException()) {
                Throwable t = result.getException();
                errorLogs(t, span);
                if (t instanceof RpcException) {
                    span.setTag("dubbo.error_code", Integer.toString(((RpcException) t).getCode()));
                }
            }
            return result;
        } catch (Throwable t) {
            errorLogs(t, span);
            if (t instanceof RpcException) {
                span.setTag("dubbo.error_code", Integer.toString(((RpcException) t).getCode()));
            }
            throw t;
        } finally {
            span.finish();
        }
    }

    class DubboAdapter implements TextMap {

        private RpcContext rpcContext;

        public DubboAdapter(RpcContext rpcContext) {
            this.rpcContext = rpcContext;
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return this.rpcContext.getAttachments().entrySet().iterator();
        }

        @Override
        public void put(String key, String value) {
            rpcContext.getAttachments().put(key, value);
        }
    }

    private void errorLogs(Throwable throwable, Span span) {
        Map<String, Object> errorLogs = new HashMap<>(2);
        errorLogs.put("event", Tags.ERROR.getKey());
        errorLogs.put("error.object", throwable);
        errorLogs.put("error.kind", throwable.getClass().getName());
        String message = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
        if (message != null) {
            errorLogs.put("message", message);
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        errorLogs.put("stack", sw.toString());
        Tags.ERROR.set(span, true);
        span.log(errorLogs);
    }
}

