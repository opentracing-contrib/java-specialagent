/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.pulsar.client;

import static org.junit.Assert.*;

import io.opentracing.contrib.specialagent.TestUtil;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.broker.PulsarService;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.loadbalance.impl.SimpleLoadManagerImpl;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.common.policies.data.ClusterData;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.apache.pulsar.zookeeper.LocalBookkeeperEnsemble;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.Logger;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class PulsarClientTest {
  private static final Logger logger = Logger.getLogger(PulsarClientTest.class);
  // Pulsar doesn't yet support the latest JDK versions. We are still on 1.8
  private static final boolean isJdkSupported = System.getProperty("java.version").startsWith("1.8.");

  private static final String CLUSTER_NAME = "test-cluster";
  private static final int ZOOKEEPER_PORT = TestUtil.nextFreePort();

  private static LocalBookkeeperEnsemble bkEnsemble;
  private static PulsarService pulsarService;

  @BeforeClass
  public static void beforeClass() throws Exception {
    if (!isJdkSupported)
      return;

    bkEnsemble = new LocalBookkeeperEnsemble(3, ZOOKEEPER_PORT, TestUtil::nextFreePort);
    bkEnsemble.start();

    final int brokerWebServicePort = TestUtil.nextFreePort();
    final int brokerServicePort = TestUtil.nextFreePort();

    final ServiceConfiguration config = new ServiceConfiguration();
    config.setClusterName(CLUSTER_NAME);
    final Set<String> superUsers = Sets.newHashSet("superUser");
    config.setSuperUserRoles(superUsers);
    config.setWebServicePort(brokerWebServicePort);
    config.setZookeeperServers("127.0.0.1" + ":" + ZOOKEEPER_PORT);
    config.setBrokerServicePort(brokerServicePort);
    config.setLoadManagerClassName(SimpleLoadManagerImpl.class.getName());
    config.setTlsAllowInsecureConnection(true);
    config.setAdvertisedAddress("localhost");

    pulsarService = new PulsarService(config);
    pulsarService.start();

    try (final PulsarAdmin admin = pulsarService.getAdminClient()) {
      final ClusterData clusterData = new ClusterData(pulsarService.getBrokerServiceUrl());
      admin.clusters().createCluster(CLUSTER_NAME, clusterData);

      final TenantInfo propAdmin = new TenantInfo();
      propAdmin.getAdminRoles().add("superUser");
      propAdmin.setAllowedClusters(Sets.newHashSet(Lists.newArrayList(CLUSTER_NAME)));

      admin.tenants().createTenant("public", propAdmin);
      admin.namespaces().createNamespace("public/default", Sets.newHashSet(CLUSTER_NAME));
    }
  }

  @AfterClass
  public static void afterClass() throws Exception {
    if (pulsarService != null)
      pulsarService.close();

    if (bkEnsemble != null)
      bkEnsemble.stop();
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) throws Exception {
    test(tracer, false);
  }

  @Test
  public void testAsync(final MockTracer tracer) throws Exception {
    test(tracer, true);
  }

  private static void test(final MockTracer tracer, final boolean async) throws Exception {
    if (!isJdkSupported) {
      logger.warning("jdk" + System.getProperty("java.version") + " is not supported by Pulsar");
      return;
    }

    try (
      final PulsarClient client = PulsarClient.builder().serviceUrl(pulsarService.getBrokerServiceUrl()).build();
      final Consumer<byte[]> consumer = client.newConsumer().topic("my-topic").subscriptionName("my-subscription").subscribe();
      final Producer<byte[]> producer = client.newProducer().topic("my-topic").create();
    ) {
      if (async)
        producer.sendAsync("My message".getBytes()).get(15, TimeUnit.SECONDS);
      else
        producer.send("My message".getBytes());

      final Message<byte[]> message;
      if (async)
        message = consumer.receiveAsync().get(15, TimeUnit.SECONDS);
      else
        message = consumer.receive();

      System.out.println("Message received: " + new String(message.getData()));
      consumer.acknowledge(message);
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    assertNull(tracer.activeSpan());
    for (final MockSpan span : spans)
      assertEquals(PulsarClientAgentIntercept.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));

    assertEquals(spans.get(0).context().traceId(), spans.get(1).context().traceId());
  }
}