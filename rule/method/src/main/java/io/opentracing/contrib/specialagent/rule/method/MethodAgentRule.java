package io.opentracing.contrib.specialagent.rule.method;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import java.util.ArrayList;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * MethodAgentRule
 *
 * @author code98@163.com
 * @date 2019/11/15 5:27 下午
 */
public class MethodAgentRule extends AgentRule {

    private static final String METHOD_CONFIG = "sa.config.instrumentation.plugin.method";

    @Override
    public Iterable<? extends AgentBuilder> buildAgent(AgentBuilder builder) throws Exception {
        List<AgentBuilder> builders = new ArrayList<>();

        String configInfos = System.getProperty(METHOD_CONFIG);
        if (configInfos == null || "".equals(configInfos)) {
            // sa.config.instrumentation.plugin.method is not config
            return builders;
        }

        String[] classMethods = configInfos.split(";");
        for (String classMethod : classMethods) {
            String[] classMethodSplit = classMethod.split("#");
            String className = classMethodSplit[0];
            String method = classMethodSplit[1];

            builders.add(
                    builder.type(named(className)).transform(new AgentBuilder.Transformer() {
                        @Override
                        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                                TypeDescription typeDescription,
                                                                ClassLoader classLoader,
                                                                JavaModule module) {
                            return builder.visit(Advice.to(MethodIntercept.class).on(named(method)));
                        }
                    })
            );
        }

        return builders;
    }

    public static class MethodIntercept {

        @Advice.OnMethodEnter
        public static void enter(final @Advice.Origin String origin) {
            if (isEnabled(origin)) {
                MethodAgentIntercept.enter(origin);
            }
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable t) {
            if (isEnabled(origin)) {
                MethodAgentIntercept.exit(t);
            }
        }
    }

}
