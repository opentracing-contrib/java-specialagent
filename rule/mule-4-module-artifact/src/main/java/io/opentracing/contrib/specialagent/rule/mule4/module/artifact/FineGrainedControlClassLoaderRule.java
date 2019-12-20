package io.opentracing.contrib.specialagent.rule.mule4.module.artifact;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Return;
import net.bytebuddy.asm.Advice.Argument;
import net.bytebuddy.asm.Advice.This;
import net.bytebuddy.asm.Advice.Origin;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.utility.JavaModule;

import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class FineGrainedControlClassLoaderRule extends AgentRule {
    @Override
    public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
        return Collections.singletonList(
                builder
                        .type(hasSuperType(named("org.mule.runtime.module.artifact.api.classloader.FineGrainedControlClassLoader")))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                                    final TypeDescription typeDescription,
                                                                    final ClassLoader classLoader, final JavaModule module) {
                                return builder.visit(Advice.to(OnExit.class).on(named("getResource")));
                            }
                        })
        );
    }

    public static class OnExit {
        @OnMethodExit
        public static void exit(final @Origin String origin, final @This Object thiz,
                                @Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
                                final @Argument(value = 0, typing = Assigner.Typing.DYNAMIC) Object resObj) {
            if (!isEnabled(origin))
                return;

            FineGrainedControlAgentIntercept.exit(thiz, returned, resObj);
        }
    }
}
