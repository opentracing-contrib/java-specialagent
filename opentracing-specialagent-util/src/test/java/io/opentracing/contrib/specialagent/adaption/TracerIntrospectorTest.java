package io.opentracing.contrib.specialagent.adaption;

import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Options;
import io.jaegertracing.internal.JaegerTracer;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;

public class TracerIntrospectorTest {

  @Test
  public void testLightStepServiceName() throws MalformedURLException {
    JRETracer tracer = new JRETracer(new Options.OptionsBuilder().withComponentName("foo").build());
    assertEquals("foo", TracerIntrospector.getServiceName(tracer));
  }

  @Test
  public void testJaegerServiceName() {
    JaegerTracer tracer = new JaegerTracer.Builder("foo").build();
    assertEquals("foo", TracerIntrospector.getServiceName(tracer));
  }
}
