/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.junit.Test;

/**
 * Tests for methods in {@link SpecialAgentUtil}.
 *
 * @author Seva Safris
 */
public class SpecialAgentUtilTest {
  public static void testParseConfiguration(final LinkedHashMap<String,String> properties) {
    final ArrayList<String> verbosePluginNames = new ArrayList<>();
    final HashMap<String,Boolean> integrationRuleNameToEnable = new HashMap<>();
    final HashMap<String,Boolean> traceExporterNameToEnable = new HashMap<>();

    final File[] includedPlugins = SpecialAgentUtil.parseConfiguration(properties, verbosePluginNames, integrationRuleNameToEnable, traceExporterNameToEnable);

    assertArrayEquals(new File[] {new File("exporter.jar")}, includedPlugins);
    final boolean allIntegrationsEnabled = !integrationRuleNameToEnable.containsKey("*") || integrationRuleNameToEnable.remove("*");
    assertFalse(allIntegrationsEnabled);
    final boolean allExportersEnabled = !traceExporterNameToEnable.containsKey("*") || traceExporterNameToEnable.remove("*");
    assertFalse(allExportersEnabled);

    assertTrue(verbosePluginNames.toString(), verbosePluginNames.contains("concurrent"));

    assertFalse(integrationRuleNameToEnable.toString(), integrationRuleNameToEnable.get("okhttp"));
    assertTrue(integrationRuleNameToEnable.toString(), integrationRuleNameToEnable.get("concurrent"));
    assertTrue(integrationRuleNameToEnable.toString(), integrationRuleNameToEnable.get("lettuce:5.?"));

    assertFalse(traceExporterNameToEnable.toString(), traceExporterNameToEnable.get("jaeger"));
    assertTrue(traceExporterNameToEnable.toString(), traceExporterNameToEnable.get("lightstep"));
  }

  @Test
  public void testParseConfiguration() {
    final LinkedHashMap<String,String> properties = new LinkedHashMap<>();
    properties.put("sa.integration.*.disable", "");
    properties.put("sa.exporter.*.disable", "");
    properties.put("sa.integration.okhttp.enable", "");
    properties.put("sa.integration.concurrent.enable", "");
    properties.put("sa.integration.concurrent.verbose", "");
    properties.put("sa.integration.lettuce:5.?.enable", "");
    properties.put("sa.integration.okhttp.disable", "");
    properties.put("sa.exporter.jaeger.enable", "");
    properties.put("sa.exporter.jaeger.enable", "false");
    properties.put("sa.exporter.lightstep.enable", "");
    properties.put("sa.include", "exporter.jar");
    testParseConfiguration(properties);
  }

  @Test
  public void testParseDeprecatedConfiguration() {
    final LinkedHashMap<String,String> properties = new LinkedHashMap<>();
    properties.put("sa.instrumentation.plugin.*.disable", "");
    properties.put("sa.tracer.plugin.*.disable", "");
    properties.put("sa.instrumentation.plugin.okhttp.enable", "");
    properties.put("sa.instrumentation.plugin.concurrent.enable", "");
    properties.put("sa.instrumentation.plugin.concurrent.verbose", "");
    properties.put("sa.instrumentation.plugin.lettuce:5.?.enable", "");
    properties.put("sa.instrumentation.plugin.okhttp.disable", "");
    properties.put("sa.tracer.plugin.jaeger.enable", "");
    properties.put("sa.tracer.plugin.jaeger.enable", "false");
    properties.put("sa.tracer.plugin.lightstep.enable", "");
    properties.put("sa.instrumentation.plugin.include", "exporter.jar");
    testParseConfiguration(properties);
  }

  @Test
  public void testDigestEventsProperty() {
    Event[] events = SpecialAgentUtil.digestEventsProperty(null);
    for (int i = 0; i < events.length; ++i)
      assertNull(events[i]);

    events = SpecialAgentUtil.digestEventsProperty("DISCOVERY,TRANSFORMATION,IGNORED,ERROR,COMPLETE");
    for (final Event event : events)
      assertNotNull(event);

    events = SpecialAgentUtil.digestEventsProperty("");
    for (final Event event : events)
      assertNull(event);

    events = SpecialAgentUtil.digestEventsProperty("DISCOVERY");
    assertNotNull(events[Event.DISCOVERY.ordinal()]);
    assertNull(events[Event.COMPLETE.ordinal()]);
    assertNull(events[Event.TRANSFORMATION.ordinal()]);
    assertNull(events[Event.ERROR.ordinal()]);
    assertNull(events[Event.IGNORED.ordinal()]);

    events = SpecialAgentUtil.digestEventsProperty("TRANSFORMATION,COMPLETE");
    assertNotNull(events[Event.TRANSFORMATION.ordinal()]);
    assertNotNull(events[Event.COMPLETE.ordinal()]);
    assertNull(events[Event.DISCOVERY.ordinal()]);
    assertNull(events[Event.ERROR.ordinal()]);
    assertNull(events[Event.IGNORED.ordinal()]);
  }
}