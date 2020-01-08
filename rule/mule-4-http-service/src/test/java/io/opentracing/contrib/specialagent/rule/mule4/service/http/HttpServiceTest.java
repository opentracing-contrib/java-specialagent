package io.opentracing.contrib.specialagent.rule.mule4.service.http;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;

import static org.junit.Assert.assertEquals;

@AgentRunner.Config(isolateClassLoader = false)
@RunWith(AgentRunner.class)
public class HttpServiceTest extends AbstractMuleTestCase {
    private static final String HOST = "localhost";
    private static final int PORT = 9875;
    private HttpServiceImplementation service;
    private SimpleUnitTestSupportSchedulerService schedulerService;
    private HttpServer server;

    @Before
    public void before(final MockTracer tracer) throws Exception {
        // clear traces
        tracer.reset();

        schedulerService = new SimpleUnitTestSupportSchedulerService();
        service = (HttpServiceImplementation) ClassUtils.instantiateClass(HttpServiceImplementation.class.getName(), new Object[]{schedulerService},
                this.getClass().getClassLoader());
        service.start();

        server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
                .setHost(HOST)
                .setPort(PORT)
                .setName("test-server")
                .build());

        server.start();
    }


    @After
    public void after() throws Exception {
        if (server != null)
            server.stop();

        if (service != null) {
            service.stop();
        }

        if (schedulerService != null) {
            schedulerService.stop();
        }
    }

    @Test
//    @TestConfig(verbose = true)
    public void httpServiceTest(final MockTracer tracer) throws Exception {
        Response response = Request.Get("http://" + HOST + ":" + PORT + "/").execute();
        assertEquals(response.returnResponse().getStatusLine().getStatusCode(), 503);
        assertEquals(1, tracer.finishedSpans().size());
    }
}
