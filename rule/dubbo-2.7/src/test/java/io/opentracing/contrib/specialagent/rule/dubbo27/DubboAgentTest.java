package io.opentracing.contrib.specialagent.rule.dubbo27;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.rule.GreeterServiceImpl;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Filter;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.ServiceLoader;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader=false)
public class DubboAgentTest {
    private static MockClient client ;

    private static MockServer server ;


    @Before
    public void before(final MockTracer tracer) {
        tracer.reset();
    }

    @BeforeClass
    public static void setup(final MockTracer tracer) {
        server = new MockServer();
        server.start();
        client = new MockClient(server.ip(), server.port());
    }

    @AfterClass
    public static void stop() {
        client.stop();
        server.stop();
    }

    @Test
    public void testNormalSpans(final MockTracer tracer) throws Exception {
        client.get().sayHello("jorge");
        List<MockSpan> mockSpans = tracer.finishedSpans();
        Assert.assertEquals(2, mockSpans.size());
        Assert.assertEquals("GreeterService/sayHello", mockSpans.get(0).operationName());
        Assert.assertEquals("GreeterService/sayHello", mockSpans.get(1).operationName());
        Assert.assertEquals("server", mockSpans.get(0).tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals("client", mockSpans.get(1).tags().get(Tags.SPAN_KIND.getKey()));
    }

    @Test
    public void testErrorSpans(final MockTracer tracer) throws Exception {
        GreeterServiceImpl.isThrowExecption = true;
        try {
            client.get().sayHello("jorge");
        } catch (Exception e) {
            Assert.assertEquals(GreeterServiceImpl.errorMesg, e.getMessage());
        }
        List<MockSpan> mockSpans = tracer.finishedSpans();
        Assert.assertEquals(2, mockSpans.size());
        Assert.assertEquals("GreeterService/sayHello", mockSpans.get(0).operationName());
        Assert.assertEquals("GreeterService/sayHello", mockSpans.get(1).operationName());
        Assert.assertEquals(true, mockSpans.get(0).tags().get(Tags.ERROR.getKey()));
        Assert.assertEquals(true, mockSpans.get(1).tags().get(Tags.ERROR.getKey()));
        GreeterServiceImpl.isThrowExecption = false;
    }
}
