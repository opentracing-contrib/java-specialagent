package io.opentracing.contrib.specialagent.adaption;

import static org.junit.Assert.*;

import java.net.MalformedURLException;

import org.junit.Test;

import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Options;

import io.jaegertracing.internal.JaegerTracer;

public class TracerIntrospectorTest {
  @Test
  public void testLightStepServiceName() throws MalformedURLException {
    final JRETracer tracer = new JRETracer(new Options.OptionsBuilder().withComponentName("foo").build());
    assertEquals("foo", TracerIntrospector.getServiceName(tracer));
  }

  @Test
  public void testJaegerServiceName() {
    final JaegerTracer tracer = new JaegerTracer.Builder("foo").build();
    assertEquals("foo", TracerIntrospector.getServiceName(tracer));
  }
}