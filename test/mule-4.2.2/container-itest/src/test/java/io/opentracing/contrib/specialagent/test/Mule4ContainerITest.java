package io.opentracing.contrib.specialagent.test;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.mule.runtime.api.util.MuleSystemProperties;
import org.mule.runtime.core.api.config.MuleProperties;
import org.mule.runtime.module.launcher.MuleContainer;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;

public class Mule4ContainerITest {
    private static final String MULE_HOME = "/src/test/mule-home";

    public static void main(String[] args) throws Exception {
        final String homeDir = new File("").getAbsolutePath() + MULE_HOME;
        final Mule4ContainerITest lock = new Mule4ContainerITest();
        System.setProperty(MuleSystemProperties.MULE_SIMPLE_LOG, "true");
        System.setProperty(MuleProperties.MULE_HOME_DIRECTORY_PROPERTY, homeDir);
        System.setProperty(MuleProperties.MULE_BASE_DIRECTORY_PROPERTY, homeDir);
        MuleContainer container = new MuleContainer(new String[]{
                "-M-Dmule.verbose.exceptions=true",
                "-M-XX:-UseBiasedLocking",
                "-M-Dfile.encoding=UTF-8",
                "-M-Dmule.timeout.disable=false"});

        try {
            container.start(false);

            // giving the runtime time to make the http listener available
            synchronized (lock) {
                lock.wait(200);
            }

            final URL obj = new URL("http://localhost:8081");
            final HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // giving grizzly-http-server time to finish the span, since it's on a diff thread
            synchronized (lock) {
                lock.wait(200);
            }

            if (200 != responseCode)
                throw new AssertionError("ERROR: response: " + responseCode);

            checkSpans("java-grizzly-http-server", "http:request", "java-grizzly-ahc", "mule:logger");
        } finally {
            container.stop();
            System.clearProperty(MuleSystemProperties.MULE_SIMPLE_LOG);
            System.clearProperty(MuleProperties.MULE_HOME_DIRECTORY_PROPERTY);
            System.clearProperty(MuleProperties.MULE_BASE_DIRECTORY_PROPERTY);
        }
    }

    private static void checkSpans(String... components) {
        MockTracer mockTracer = (MockTracer) TestUtil.getGlobalTracer();
        System.out.println("Spans: " + mockTracer.finishedSpans());
        Map<String, MockSpan> finishedSpans = mockTracer.finishedSpans()
                .stream()
                .peek(mockSpan -> {
                    System.out.println("Span: " + mockSpan);
                    System.out.println("\tComponent: " + mockSpan.tags().get(Tags.COMPONENT.getKey()));
                    System.out.println("\tTags: " + mockSpan.tags());
                    System.out.println("\tLogs: ");
                    for (final MockSpan.LogEntry logEntry : mockSpan.logEntries())
                        System.out.println("\t" + logEntry.fields());
                })
                .collect(Collectors.toMap(mockSpan -> (String) mockSpan.tags().get(Tags.COMPONENT.getKey()),
                        mockSpan -> mockSpan));

        for (String component : components) {
            if (!finishedSpans.containsKey(component))
                throw new AssertionError("ERROR: " + component + " span not found");
        }

        mockTracer.reset();
    }
}
