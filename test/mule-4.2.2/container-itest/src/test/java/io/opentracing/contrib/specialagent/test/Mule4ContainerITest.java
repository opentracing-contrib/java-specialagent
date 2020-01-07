package io.opentracing.contrib.specialagent.test;

import org.mule.runtime.core.api.config.MuleProperties;
import org.mule.runtime.module.launcher.MuleContainer;

import java.io.File;

public class Mule4ContainerITest {

    private static final String MULE_HOME = "/src/test/mule-home";

    public static void main(String[] args) throws Exception {
        final String homeDir = new File("").getAbsolutePath() + MULE_HOME;
        System.setProperty("mule.simpleLog", "true");
        System.setProperty(MuleProperties.MULE_HOME_DIRECTORY_PROPERTY, homeDir);
        System.setProperty(MuleProperties.MULE_BASE_DIRECTORY_PROPERTY, homeDir);
        MuleContainer container = new MuleContainer(new String[]{"-M-Dmule.forceConsoleLog",
                "-M-Dmule.verbose.exceptions=true",
                "-M-XX:-UseBiasedLocking",
                "-M-Dfile.encoding=UTF-8",
                "-M-Dmule.timeout.disable=false"});

        container.start(false);

        // TODO: 1/7/20 write http request and span assertions

        container.stop();
    }
}
