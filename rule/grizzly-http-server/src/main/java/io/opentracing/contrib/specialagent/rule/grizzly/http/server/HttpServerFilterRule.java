package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import io.opentracing.Scope;
import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpResponsePacket;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class HttpServerFilterRule extends AgentRule {
    @Override
    public AgentBuilder buildAgentChainedGlobal2(final AgentBuilder builder) {
        return builder
                // TODO: 7/10/20 - Figure out how to match only to HttpServerFilter but still advice HttpCodecFilter's handleRead
                .type(named("org.glassfish.grizzly.http.HttpCodecFilter"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(HandleReadAdvice.class).on(named("handleRead")
                                .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
                                .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpHeader")))
                                .and(isPublic())));
                    }
                })
                .type(named("org.glassfish.grizzly.http.HttpServerFilter"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(advice(typeDescription).to(PrepareResponseAdvice.class).on(named("prepareResponse")
                                .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
                                .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpRequestPacket")))
                                .and(takesArgument(2, named("org.glassfish.grizzly.http.HttpResponsePacket")))
                                .and(takesArgument(3, named("org.glassfish.grizzly.http.HttpContent")))
                                .and(isPrivate())));
                }});
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

    public static class HandleReadAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(
                final @ClassName String className,
                final @Advice.Origin String origin,
                @Advice.Argument(0) final FilterChainContext ctx,
                @Advice.Argument(1) final HttpHeader httpHeader,
                @Advice.Local("scope") Scope scope) {
            if (isAllowed(className, origin))
                scope = HttpServerFilterIntercept.onHandleReadEnter(ctx, httpHeader);
        }

        @Advice.OnMethodExit
        public static void onExit(
                final @ClassName String className,
                final @Advice.Origin String origin,
                @Advice.Argument(0) final FilterChainContext ctx,
                @Advice.Return NextAction toReturn,
                @Advice.Local("scope") Scope scope) {
            if (isAllowed(className, origin))
                HttpServerFilterIntercept.onHandleReadExit(ctx, toReturn, scope);
        }
    }
}
