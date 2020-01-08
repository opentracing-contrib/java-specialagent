package io.opentracing.contrib.specialagent.test;

import io.opentracing.contrib.specialagent.TestUtil;
import org.mule.runtime.core.api.config.MuleProperties;
import org.mule.runtime.module.launcher.MuleContainer;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

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

        try {
            container.start(false);

            final URL obj = new URL("http://localhost:8081");
            final HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (200 != responseCode)
                throw new AssertionError("ERROR: response: " + responseCode);

            TestUtil.checkSpan("java-grizzly-ahc", 2);
            TestUtil.checkSpan("java-grizzly-http-server", 2);
        } finally {
            container.stop();
        }
    }
}
