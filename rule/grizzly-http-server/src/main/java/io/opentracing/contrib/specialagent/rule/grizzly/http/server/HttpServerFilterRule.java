package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import io.opentracing.contrib.specialagent.Level;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class HttpServerFilterRule extends AgentRule {
    public static final Logger logger = Logger.getLogger(HttpServerFilterRule.class);
    private static final String FILTER_CHAIN_CONTEXT = "org.glassfish.grizzly.filterchain.FilterChainContext";
    private static final String HANDLE_READ = "handleRead";

    @Override
    public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
        return builder
                .type(named("org.glassfish.grizzly.http.HttpServerFilter"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(HandleReadAdvice.class).on(named(HANDLE_READ)
                                .and(takesArgument(0, named(FILTER_CHAIN_CONTEXT)))));
                    }
                })
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(PrepareResponseAdvice.class).on(named("prepareResponse")
                                .and(takesArgument(0, named(FILTER_CHAIN_CONTEXT)))
                                .and(takesArgument(2, named("org.glassfish.grizzly.http.HttpResponsePacket")))));
                    }
                })

                .type(hasSuperClass(named("org.glassfish.grizzly.filterchain.BaseFilter"))
                        // common http server filters
                        .and(not(named("org.glassfish.grizzly.filterchain.TransportFilter")
                                .or(named("org.glassfish.grizzly.nio.transport.TCPNIOTransportFilter"))
                                .or(named("org.glassfish.grizzly.http.HttpServerFilter"))
                                .or(named("org.glassfish.grizzly.http.HttpCodecFilter"))
                                .or(named("org.glassfish.grizzly.utils.IdleTimeoutFilter"))
                                // common http client filters
                                .or(named("com.ning.http.client.providers.grizzly.AsyncHttpClientFilter"))
                                .or(named("org.glassfish.grizzly.websockets.WebSocketClientFilter"))
                                .or(hasSuperClass(named("org.glassfish.grizzly.http.HttpClientFilter"))))))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(WorkerHandleReadAdvice.class).on(named(HANDLE_READ)
                                .and(takesArgument(0, named(FILTER_CHAIN_CONTEXT)))));
                    }
                });
    }

    public static class HandleReadAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                final @ClassName String className,
                final @Advice.Origin String origin,
                @Advice.Argument(0) final Object ctx,
                @Advice.Return Object toReturn) {
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
                @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final Object ctx,
                @Advice.Local("scope") Scope scope) {

            if (hackShouldFilter(thiz))
                return;

            if (isAllowed(className, origin))
                scope = WorkerFilterIntercept.onHandleReadEnter(ctx);
        }

        @Advice.OnMethodExit
        public static void onExit(
                final @ClassName String className,
                final @Advice.Origin String origin,
                final @Advice.This Object thiz,
                @Advice.Local("scope") Scope scope) {

            if (hackShouldFilter(thiz))
                return;

            if (isAllowed(className, origin))
                WorkerFilterIntercept.onHandleReadExit(scope);
        }
    }

    public static class PrepareResponseAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                final @ClassName String className,
                final @Advice.Origin String origin,
                @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final Object ctx,
                @Advice.Argument(value = 2, typing = Assigner.Typing.DYNAMIC) final Object response) {

            if (isAllowed(className, origin))
                HttpServerFilterIntercept.onPrepareResponse(ctx, response);
        }

    }

    public static boolean hackShouldFilter(Object thiz) {
        // TODO: 7/11/20 figure out why these are not filtered at TypeDescription
        logger.log(Level.FINER, "Checking predicate for potential worker filter " + thiz.getClass().getName());
        return "com.ning.http.client.providers.grizzly.AsyncHttpClientFilter".equals(thiz.getClass().getName()) ||
                "org.glassfish.grizzly.websockets.WebSocketClientFilter".equals(thiz.getClass().getName());
    }
}
