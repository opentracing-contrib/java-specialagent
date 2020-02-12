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

package io.opentracing.contrib.specialagent.test.pulsar.functions;

import static org.apache.pulsar.functions.utils.functioncache.FunctionCacheEntry.JAVA_INSTANCE_JAR_PROPERTY;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.spy;

import com.google.common.collect.Sets;
import io.opentracing.contrib.specialagent.TestUtil;
import java.io.File;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.FileUtils;
import org.apache.pulsar.broker.PulsarService;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.loadbalance.impl.SimpleLoadManagerImpl;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.common.functions.Utils;
import org.apache.pulsar.common.policies.data.ClusterData;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.apache.pulsar.common.util.FutureUtil;
import org.apache.pulsar.functions.api.examples.ExclamationFunction;
import org.apache.pulsar.functions.worker.WorkerConfig;
import org.apache.pulsar.functions.worker.WorkerService;
import org.apache.pulsar.zookeeper.LocalBookkeeperEnsemble;

public class PulsarFunctionsITest {
  private static final String CLUSTER_NAME = "use";
  private static final int ZOOKEEPER_PORT = 8880;
  private static final AtomicInteger port = new AtomicInteger(ZOOKEEPER_PORT);
  private static final int brokerWebServicePort = 8885;
  private static final int brokerServicePort = 8886;
  private static final int workerServicePort = 9999;
  private static WorkerConfig workerConfig;
  private static final String tenant = "external-repl-prop";

  public static void main(String[] args) throws Exception {
    start();
    shutdown();
  }

  static void start() throws Exception {
    // delete all function temp files
    File dir = new File(System.getProperty("java.io.tmpdir"));
    File[] foundFiles = dir.listFiles((dir1, name) -> name.startsWith("function"));

    if (foundFiles != null) {
      for (File file : foundFiles) {
        FileUtils.deleteQuietly(file);
      }
    }

    // Start local bookkeeper ensemble
    LocalBookkeeperEnsemble bkEnsemble = new LocalBookkeeperEnsemble(3, ZOOKEEPER_PORT,
        port::incrementAndGet);
    bkEnsemble.start();

    String brokerServiceUrl = "http://127.0.0.1:" + brokerWebServicePort;

    ServiceConfiguration config = spy(new ServiceConfiguration());
    config.setClusterName(CLUSTER_NAME);
    Set<String> superUsers = Sets.newHashSet("superUser");
    config.setSuperUserRoles(superUsers);
    config.setWebServicePort(Optional.of(brokerWebServicePort));
    config.setZookeeperServers("127.0.0.1" + ":" + ZOOKEEPER_PORT);
    config.setBrokerServicePort(Optional.of(brokerServicePort));
    config.setLoadManagerClassName(SimpleLoadManagerImpl.class.getName());
    config.setTlsAllowInsecureConnection(true);
    config.setAdvertisedAddress("localhost");

    config.setAuthenticationEnabled(false);
    config.setAuthorizationEnabled(false);

    config.setBrokerClientTlsEnabled(false);
    config.setAllowAutoTopicCreationType("non-partitioned");

    WorkerService functionsWorkerService = createPulsarFunctionWorker(config);
    URL urlTls = new URL(brokerServiceUrl);
    Optional<WorkerService> functionWorkerService = Optional.of(functionsWorkerService);
    PulsarService pulsar = new PulsarService(config, functionWorkerService);
    pulsar.start();

    try (PulsarAdmin admin =
        PulsarAdmin.builder().serviceHttpUrl(brokerServiceUrl)
            .allowTlsInsecureConnection(true).build()) {

      // update cluster metadata
      ClusterData clusterData = new ClusterData(urlTls.toString());
      admin.clusters().updateCluster(config.getClusterName(), clusterData);

      TenantInfo propAdmin = new TenantInfo();
      propAdmin.getAdminRoles().add("superUser");
      propAdmin.setAllowedClusters(Sets.newHashSet(CLUSTER_NAME));
      admin.tenants().updateTenant(tenant, propAdmin);

      String jarFilePathUrl = Utils.FILE + ":"
          + ExclamationFunction.class.getProtectionDomain().getCodeSource().getLocation().getPath();

      ClientBuilder clientBuilder = PulsarClient.builder()
          .serviceUrl(workerConfig.getPulsarServiceUrl());
      try (PulsarClient pulsarClient = clientBuilder.build()) {
        testE2EPulsarFunction(jarFilePathUrl, admin, pulsarClient);
      }
    }

  }

  private static void testE2EPulsarFunction(String jarFilePathUrl, PulsarAdmin admin,
      PulsarClient pulsarClient) throws Exception {

    final String namespacePortion = "io";
    final String replNamespace = tenant + "/" + namespacePortion;
    final String sourceTopic = "persistent://" + replNamespace + "/my-topic1";
    final String sinkTopic = "persistent://" + replNamespace + "/output";
    final String sinkTopic2 = "persistent://" + replNamespace + "/output2";
    final String functionName = "PulsarFunction-test";
    final String subscriptionName = "test-sub";
    admin.namespaces().createNamespace(replNamespace);
    Set<String> clusters = Sets.newHashSet(CLUSTER_NAME);
    admin.namespaces().setNamespaceReplicationClusters(replNamespace, clusters);

    // create a producer that creates a topic at broker
    try (Producer<String> producer = pulsarClient.newProducer(Schema.STRING).topic(sourceTopic)
        .create();
        Consumer<String> consumer = pulsarClient.newConsumer(Schema.STRING).topic(sinkTopic2)
            .subscriptionName("sub").subscribe()) {

      FunctionConfig functionConfig = createFunctionConfig(namespacePortion, functionName,
          "my.*", sinkTopic, subscriptionName);
      functionConfig.setProcessingGuarantees(FunctionConfig.ProcessingGuarantees.ATLEAST_ONCE);
      functionConfig.setParallelism(1);
      functionConfig.setOutput(sinkTopic2);

      admin.functions().createFunctionWithUrl(functionConfig, jarFilePathUrl);

      await().atMost(15, TimeUnit.SECONDS).until(
          () -> admin.topics().getStats(sourceTopic).subscriptions.size(), equalTo(1));

      TestUtil.resetTracer();
      producer.newMessage().value("my-message").send();

      await().atMost(15, TimeUnit.SECONDS).until(
          () -> admin.topics().getStats(sourceTopic).subscriptions
              .get(subscriptionName).unackedMessages, equalTo(0L));

      consumer.receive(15, TimeUnit.SECONDS);
    }

    TestUtil.checkSpan("java-pulsar-functions", 5);
  }

  private static FunctionConfig createFunctionConfig(String namespace,
      String functionName, String sourceTopic, String sinkTopic, String subscriptionName) {

    String sourceTopicPattern = String
        .format("persistent://%s/%s/%s", tenant, namespace, sourceTopic);

    FunctionConfig functionConfig = new FunctionConfig();
    functionConfig.setTenant(tenant);
    functionConfig.setNamespace(namespace);
    functionConfig.setName(functionName);
    functionConfig.setParallelism(1);
    functionConfig.setProcessingGuarantees(FunctionConfig.ProcessingGuarantees.EFFECTIVELY_ONCE);
    functionConfig.setSubName(subscriptionName);
    functionConfig.setTopicsPattern(sourceTopicPattern);
    functionConfig.setAutoAck(true);
    functionConfig.setClassName("org.apache.pulsar.functions.api.examples.ExclamationFunction");
    functionConfig.setRuntime(FunctionConfig.Runtime.JAVA);
    functionConfig.setOutput(sinkTopic);
    functionConfig.setCleanupSubscription(true);
    return functionConfig;
  }

  private static WorkerService createPulsarFunctionWorker(ServiceConfiguration config) {

    System.setProperty(JAVA_INSTANCE_JAR_PROPERTY,
        FutureUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath());

    workerConfig = new WorkerConfig();
    String pulsarFunctionsNamespace = tenant + "/pulsar-function-admin";
    workerConfig.setPulsarFunctionsNamespace(pulsarFunctionsNamespace);
    workerConfig.setSchedulerClassName(
        org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler.class.getName());
    workerConfig.setThreadContainerFactory(
        new WorkerConfig.ThreadContainerFactory().setThreadGroupName("use"));
    // worker talks to local broker
    workerConfig.setPulsarServiceUrl("pulsar://127.0.0.1:" + brokerServicePort);
    workerConfig.setPulsarWebServiceUrl("http://127.0.0.1:" + brokerWebServicePort);
    workerConfig.setFailureCheckFreqMs(10000);
    workerConfig.setNumFunctionPackageReplicas(1);
    workerConfig.setClusterCoordinationTopicName("coordinate");
    workerConfig.setFunctionAssignmentTopicName("assignment");
    workerConfig.setFunctionMetadataTopicName("metadata");
    workerConfig.setInstanceLivenessCheckFreqMs(10000);
    workerConfig.setWorkerPort(workerServicePort);
    workerConfig.setPulsarFunctionsCluster(config.getClusterName());
    String hostname = "localhost";
    String workerId =
        "c-" + config.getClusterName() + "-fw-" + hostname + "-" + workerConfig.getWorkerPort();
    workerConfig.setWorkerHostname(hostname);
    workerConfig.setWorkerId(workerId);

    workerConfig.setTlsAllowInsecureConnection(true);

    workerConfig.setAuthenticationEnabled(false);
    workerConfig.setAuthorizationEnabled(false);

    return new WorkerService(workerConfig);
  }

  private static void shutdown() {
    // Embedded Zookeeper processes may not exit
    System.exit(0);
  }

}