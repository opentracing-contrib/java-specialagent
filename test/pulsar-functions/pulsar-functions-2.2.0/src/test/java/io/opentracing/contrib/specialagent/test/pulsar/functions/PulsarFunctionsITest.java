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

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pulsar.broker.PulsarService;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.loadbalance.impl.SimpleLoadManagerImpl;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.common.policies.data.ClusterData;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.apache.pulsar.functions.api.utils.IdentityFunction;
import org.apache.pulsar.functions.proto.Function;
import org.apache.pulsar.functions.proto.Function.FunctionDetails;
import org.apache.pulsar.functions.proto.Function.ProcessingGuarantees;
import org.apache.pulsar.functions.proto.Function.SinkSpec;
import org.apache.pulsar.functions.proto.Function.SourceSpec;
import org.apache.pulsar.functions.sink.PulsarSink;
import org.apache.pulsar.functions.utils.Reflections;
import org.apache.pulsar.functions.utils.Utils;
import org.apache.pulsar.functions.worker.WorkerConfig;
import org.apache.pulsar.functions.worker.WorkerService;
import org.apache.pulsar.zookeeper.LocalBookkeeperEnsemble;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class PulsarFunctionsITest {
  private static final String CLUSTER_NAME = "use";
  private static final int ZOOKEEPER_PORT = 8880;
  private static final AtomicInteger port = new AtomicInteger(ZOOKEEPER_PORT);
  private static final String tenant = "external-repl-prop";
  private static final int brokerWebServicePort = 8885;
  private static final int brokerServicePort = 8886;
  private static final int workerServicePort = 9999;
  private static WorkerConfig workerConfig;
  private static final String pulsarFunctionsNamespace = tenant + "/use/pulsar-function-admin";

  public static void main(final String[] args) throws Exception {
    start();
    shutdown();
  }

  static void start() throws Exception {
    // Start local bookkeeper ensemble
    final LocalBookkeeperEnsemble bkEnsemble = new LocalBookkeeperEnsemble(3, ZOOKEEPER_PORT, port::incrementAndGet);
    bkEnsemble.start();

    final String brokerServiceUrl = "http://127.0.0.1:" + brokerWebServicePort;

    final ServiceConfiguration config = new ServiceConfiguration();
    config.setClusterName(CLUSTER_NAME);
    final Set<String> superUsers = Sets.newHashSet("superUser");
    config.setSuperUserRoles(superUsers);

    config.setZookeeperServers("127.0.0.1" + ":" + ZOOKEEPER_PORT);
    config.setLoadManagerClassName(SimpleLoadManagerImpl.class.getName());
    config.setWebServicePort(brokerWebServicePort);
    config.setBrokerServicePort(brokerServicePort);

    config.setAuthenticationEnabled(false);
    config.setTlsEnabled(false);
    config.setTlsAllowInsecureConnection(true);
    config.setAdvertisedAddress("localhost");

    final WorkerService functionsWorkerService = createPulsarFunctionWorker(config);
    final URL urlTls = new URL(brokerServiceUrl);
    final Optional<WorkerService> functionWorkerService = Optional.of(functionsWorkerService);
    try (final PulsarService pulsar = new PulsarService(config, functionWorkerService)) {
      pulsar.start();
      try (final PulsarAdmin admin = PulsarAdmin.builder().serviceHttpUrl(brokerServiceUrl).allowTlsInsecureConnection(true).build()) {
        // update cluster metadata
        final ClusterData clusterData = new ClusterData(urlTls.toString());
        admin.clusters().updateCluster(config.getClusterName(), clusterData);

        final ClientBuilder clientBuilder = PulsarClient.builder().serviceUrl(workerConfig.getPulsarServiceUrl());
        try (final PulsarClient pulsarClient = clientBuilder.build()) {
          final TenantInfo propAdmin = new TenantInfo();
          propAdmin.getAdminRoles().add("superUser");
          propAdmin.setAllowedClusters(Sets.newHashSet(CLUSTER_NAME));
          admin.tenants().updateTenant(tenant, propAdmin);

          testPulsarFunction(admin, pulsarClient);
        }
      }
    }
  }

  private static WorkerService createPulsarFunctionWorker(final ServiceConfiguration config) {
    workerConfig = new WorkerConfig();
    workerConfig.setPulsarFunctionsNamespace(pulsarFunctionsNamespace);
    workerConfig.setSchedulerClassName(org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler.class.getName());
    workerConfig.setThreadContainerFactory(new WorkerConfig.ThreadContainerFactory().setThreadGroupName("use"));
    // worker talks to local broker
    workerConfig.setPulsarServiceUrl("pulsar://127.0.0.1:" + brokerServicePort);
    workerConfig.setPulsarWebServiceUrl("http://127.0.0.1:" + brokerWebServicePort);
    workerConfig.setFailureCheckFreqMs(30000);
    workerConfig.setNumFunctionPackageReplicas(0);
    workerConfig.setClusterCoordinationTopicName("coordinate");
    workerConfig.setFunctionAssignmentTopicName("assignment");
    workerConfig.setFunctionMetadataTopicName("metadata");
    workerConfig.setInstanceLivenessCheckFreqMs(30000);
    workerConfig.setWorkerPort(workerServicePort);

    workerConfig.setPulsarFunctionsCluster(config.getClusterName());
    final String hostname = "localhost";
    final String workerId = "c-" + config.getClusterName() + "-fw-" + hostname + "-";
    workerConfig.setWorkerHostname(hostname);
    workerConfig.setWorkerId(workerId);

    workerConfig.setUseTls(false);
    workerConfig.setTlsAllowInsecureConnection(true);

    workerConfig.setAuthenticationEnabled(false);
    workerConfig.setAuthorizationEnabled(false);

    return new WorkerService(workerConfig);
  }

  public static void testPulsarFunction(final PulsarAdmin admin, final PulsarClient pulsarClient) throws Exception {
    final String namespacePortion = "io";
    final String replNamespace = tenant + "/" + namespacePortion;
    final String sourceTopic = "persistent://" + replNamespace + "/my-topic1";
    final String sinkTopic = "persistent://" + replNamespace + "/output";
    final String functionName = "PulsarSink-test";
    final String subscriptionName = "test-sub";
    admin.namespaces().createNamespace(replNamespace);
    final Set<String> clusters = Sets.newHashSet(CLUSTER_NAME);
    admin.namespaces().setNamespaceReplicationClusters(replNamespace, clusters);

    try (final Consumer<byte[]> consumer = pulsarClient.newConsumer().topic(sinkTopic).subscriptionName("sub").subscribe()) {
      try (final Producer<byte[]> producer = pulsarClient.newProducer().topic(sourceTopic).create()) {
        final String jarFilePathUrl = Utils.FILE + ":" + PulsarSink.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        final FunctionDetails functionDetails = createSinkConfig(jarFilePathUrl, namespacePortion, functionName, "my.*", sinkTopic, subscriptionName);
        admin.functions().createFunctionWithUrl(functionDetails, jarFilePathUrl);

        await().atMost(15, TimeUnit.SECONDS).until(() -> admin.topics().getStats(sourceTopic).subscriptions.size(), equalTo(1));

        // validate pulsar sink consumer has started on the topic
        if (admin.topics().getStats(sourceTopic).subscriptions.size() != 1)
          throw new AssertionError("Pulsar sink consumer has not started on the topic");

        TestUtil.resetTracer();
        producer.newMessage().value("my-message".getBytes()).send();
        consumer.receive(15, TimeUnit.SECONDS);
      }
    }

    TestUtil.checkSpan(new ComponentSpanCount("java-pulsar-functions", 1));
  }

  private static FunctionDetails createSinkConfig(final String jarFile, final String namespace, final String functionName, final String sourceTopic, final String sinkTopic, final String subscriptionName) {
    final File file = new File(jarFile);
    try {
      Reflections.loadJar(file);
    }
    catch (final MalformedURLException e) {
      throw new RuntimeException("Failed to load user jar " + file, e);
    }

    final String sourceTopicPattern = String.format("persistent://%s/%s/%s", tenant, namespace, sourceTopic);
    final Class<?> typeArg = byte[].class;

    final FunctionDetails.Builder functionDetailsBuilder = FunctionDetails.newBuilder();
    functionDetailsBuilder.setTenant(tenant);
    functionDetailsBuilder.setNamespace(namespace);
    functionDetailsBuilder.setName(functionName);
    functionDetailsBuilder.setRuntime(FunctionDetails.Runtime.JAVA);
    functionDetailsBuilder.setParallelism(1);
    functionDetailsBuilder.setClassName(IdentityFunction.class.getName());
    functionDetailsBuilder.setProcessingGuarantees(ProcessingGuarantees.EFFECTIVELY_ONCE);

    // set source spec
    // source spec classname should be empty so that the default pulsar source
    // will be used
    final SourceSpec.Builder sourceSpecBuilder = SourceSpec.newBuilder();
    sourceSpecBuilder.setSubscriptionType(Function.SubscriptionType.FAILOVER);
    sourceSpecBuilder.setTypeClassName(typeArg.getName());
    sourceSpecBuilder.setTopicsPattern(sourceTopicPattern);
    sourceSpecBuilder.setSubscriptionName(subscriptionName);
    sourceSpecBuilder.putTopicsToSerDeClassName(sourceTopicPattern, "");
    functionDetailsBuilder.setAutoAck(true);
    functionDetailsBuilder.setSource(sourceSpecBuilder);

    // set up sink spec
    final SinkSpec.Builder sinkSpecBuilder = SinkSpec.newBuilder();
    // sinkSpecBuilder.setClassName(PulsarSink.class.getName());
    sinkSpecBuilder.setTopic(sinkTopic);
    final Map<String,Object> sinkConfigMap = Maps.newHashMap();
    sinkSpecBuilder.setConfigs(new Gson().toJson(sinkConfigMap));
    sinkSpecBuilder.setTypeClassName(typeArg.getName());
    functionDetailsBuilder.setSink(sinkSpecBuilder);

    return functionDetailsBuilder.build();
  }

  private static void shutdown() {
    // Embedded Zookeeper processes may not exit
    System.exit(0);
  }
}