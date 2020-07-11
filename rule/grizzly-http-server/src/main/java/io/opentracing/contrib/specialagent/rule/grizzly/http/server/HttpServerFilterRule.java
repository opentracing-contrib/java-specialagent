package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.StringMatcher;
import net.bytebuddy.utility.JavaModule;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpResponsePacket;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class HttpServerFilterRule extends AgentRule {
    @Override
    public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
        return builder
                .type(named("org.glassfish.grizzly.http.HttpServerFilter"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(HandleReadAdvice.class).on(named("handleRead")
                                .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
                                .and(isPublic())));
                    }
                })
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(PrepareResponseAdvice.class).on(named("prepareResponse")
                                .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
                                .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpRequestPacket")))
                                .and(takesArgument(2, named("org.glassfish.grizzly.http.HttpResponsePacket")))
                                .and(takesArgument(3, named("org.glassfish.grizzly.http.HttpContent")))
                                .and(isPrivate())));
                    }
                })

                .type(hasSuperType(named("org.glassfish.grizzly.filterchain.BaseFilter"))
                        // common grizzly filters
                        .and(not(named("org.glassfish.grizzly.filterchain.TransportFilter")))
                        .and(not(named("org.glassfish.grizzly.nio.transport.TCPNIOTransportFilter")))
                        // TODO: 7/11/20 Figure out why HttpServerFilter still matches
                        .and(not(named("org.glassfish.grizzly.http.HttpServerFilter")))
                        .and(not(named("org.glassfish.grizzly.utils.IdleTimeoutFilter")))
                        .and(not(named("org.glassfish.grizzly.http.server.FileCacheFilter")))
                )
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(WorkerHandleReadAdvice.class).on(named("handleRead")
                                .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
                                .and(isPublic())));
                    }
                });
    }

    public static class HandleReadAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                final @ClassName String className,
                final @Advice.Origin String origin,
                @Advice.Argument(0) final FilterChainContext ctx,
                @Advice.Return NextAction toReturn) {
            if (isAllowed(className, origin))
                HttpServerFilterIntercept.onHandleReadExit(ctx, toReturn);
        }
    }

    public static class WorkerHandleReadAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(
                final @ClassName String className,
                final @Advice.Origin String origin,
                final @Advice.This Object thiz,
                @Advice.Argument(0) final FilterChainContext ctx,
                @Advice.Local("scope") Scope scope) {

            // manually filtering HttpServerFilter
            if (new StringMatcher("org.glassfish.grizzly.http.HttpServerFilter",
                    StringMatcher.Mode.EQUALS_FULLY).matches(thiz.getClass().getName())) {
                return;
            }

            if (isAllowed(className, origin))
                scope = WorkerFilterIntercept.onHandleReadEnter(ctx);
        }

        @Advice.OnMethodExit
        public static void onExit(
                final @ClassName String className,
                final @Advice.Origin String origin,
                @Advice.Local("scope") Scope scope) {
            if (isAllowed(className, origin))
                WorkerFilterIntercept.onHandleReadExit(scope);
        }
    }

    public static class PrepareResponseAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                final @ClassName String className,
                final @Advice.Origin String origin,
                @Advice.Argument(0) final FilterChainContext ctx,
                @Advice.Argument(2) final HttpResponsePacket response) {
            if (isAllowed(className, origin))
                HttpServerFilterIntercept.onPrepareResponse(ctx, response);
        }

    }
}
