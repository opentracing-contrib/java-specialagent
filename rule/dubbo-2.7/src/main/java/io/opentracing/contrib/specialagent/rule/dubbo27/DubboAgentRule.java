package io.opentracing.contrib.specialagent.rule.dubbo27;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;
import org.apache.dubbo.rpc.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class DubboAgentRule extends AgentRule {


    @Override
    public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
        return Arrays.asList(builder
                .type(named("org.apache.dubbo.common.extension.ExtensionLoader"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
                        return builder.visit(Advice.to(DubboAgentRule.class).on(named("getActivateExtension")));
                    }}));
    }

    @Advice.OnMethodExit
    public static void exit(final @Advice.Origin String origin,@Advice.Argument(value = 1) Object  key,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned) {
        if (key instanceof String) {
            if ("service.filter".equals(key)) {
                if (isEnabled("DubboAgentRule", origin)) {
                    returned = DubboAgentIntercept.exit(returned);
                }
            } else if ("reference.filter".equals(key)) {
                if (isEnabled("DubboAgentRule", origin)) {
                    returned = DubboAgentIntercept.exit(returned);
                }
            }
        }
    }

}
