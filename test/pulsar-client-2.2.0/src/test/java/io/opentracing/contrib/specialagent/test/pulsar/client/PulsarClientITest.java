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

package io.opentracing.contrib.specialagent.test.pulsar.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.opentracing.contrib.specialagent.TestUtil;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

public class PulsarClientITest {
  // Pulsar doesn't yet support the latest JDK versions. We are still on 1.8
  private static final boolean isJdkSupported = System.getProperty("java.version").startsWith("1.8.");

  private static final String CLUSTER_NAME = "test-cluster";
  private static final int ZOOKEEPER_PORT = 8880;
  private static final AtomicInteger port = new AtomicInteger(ZOOKEEPER_PORT);

  public static void main(final String[] args) throws Exception {
    if (!isJdkSupported)
      return;

    LocalBookkeeperEnsemble bkEnsemble = new LocalBookkeeperEnsemble(3, ZOOKEEPER_PORT, port::incrementAndGet);
    bkEnsemble.startStandalone();

    int brokerWebServicePort = 8885;
    int brokerServicePort = 8886;

    ServiceConfiguration config = new ServiceConfiguration();
    config.setClusterName(CLUSTER_NAME);
    Set<String> superUsers = Sets.newHashSet("superUser");
    config.setSuperUserRoles(superUsers);

    Method setWebServicePortInt = null;
    Method setWebServicePortOpt = null;
    try {
      setWebServicePortInt = config.getClass()
          .getMethod("setWebServicePort", Integer.TYPE);
    } catch (NoSuchMethodException ignore) {
      setWebServicePortOpt = config.getClass()
          .getMethod("setWebServicePort", Optional.class);
    }

    Method setBrokerServicePortInt = null;
    Method setBrokerServicePortOpt = null;
    try {
      setBrokerServicePortInt = config.getClass()
          .getMethod("setBrokerServicePort", Integer.TYPE);
    } catch (NoSuchMethodException ignore) {
      setBrokerServicePortOpt = config.getClass()
          .getMethod("setBrokerServicePort", Optional.class);
    }

    if(setWebServicePortInt != null) {
      setWebServicePortInt.invoke(config, brokerWebServicePort);
    } else if(setWebServicePortOpt != null) {
      setWebServicePortOpt.invoke(config, Optional.of(brokerWebServicePort));
    }

    if(setBrokerServicePortInt != null) {
      setBrokerServicePortInt.invoke(config, brokerServicePort);
    } else if(setBrokerServicePortOpt != null) {
      setBrokerServicePortOpt.invoke(config, Optional.of(brokerServicePort));
    }

    config.setZookeeperServers("127.0.0.1" + ":" + ZOOKEEPER_PORT);
    config.setLoadManagerClassName(SimpleLoadManagerImpl.class.getName());
    config.setTlsAllowInsecureConnection(true);
    config.setAdvertisedAddress("localhost");

    PulsarService pulsarService = new PulsarService(config);

    pulsarService.start();

    try(final PulsarClient client = PulsarClient.builder().serviceUrl(pulsarService.getBrokerServiceUrl()).build()) {
      try (final PulsarAdmin admin = pulsarService.getAdminClient()) {
        ClusterData clusterData = new ClusterData(pulsarService.getBrokerServiceUrl());
        admin.clusters().createCluster(CLUSTER_NAME, clusterData);

        TenantInfo propAdmin = new TenantInfo();
        propAdmin.getAdminRoles().add("superUser");
        propAdmin.setAllowedClusters(Sets.newHashSet(Lists.newArrayList(CLUSTER_NAME)));

        admin.tenants().createTenant("public", propAdmin);
        admin.namespaces().createNamespace("public/default", Sets.newHashSet(CLUSTER_NAME));
      }


      try (final Consumer<byte[]> consumer = client.newConsumer().topic("my-topic")
          .subscriptionName("my-subscription").subscribe()) {

        try (final Producer<byte[]> producer = client.newProducer().topic("my-topic").create()) {

          TestUtil.resetTracer();
          producer.sendAsync("My message".getBytes()).get(15, TimeUnit.SECONDS);
          Message<byte[]> message = consumer.receive();

          System.out.printf("Message received: %s\n", new String(message.getData()));
          consumer.acknowledge(message);

        }
      }
    }

    pulsarService.close();
    bkEnsemble.stop();

    TestUtil.checkSpan("pulsar", 2, true);

    // Embedded Zookeeper processes may not exit
    System.exit(0);
  }


}