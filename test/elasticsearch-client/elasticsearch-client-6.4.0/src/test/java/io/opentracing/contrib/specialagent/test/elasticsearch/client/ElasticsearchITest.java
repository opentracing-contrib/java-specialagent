/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.test.elasticsearch.client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeFactory;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import io.opentracing.contrib.specialagent.TestUtil;

public class ElasticsearchITest {
  private static final int HTTP_PORT = 9205;
  private static final int HTTP_TRANSPORT_PORT = 9305;
  private static final String ES_WORKING_DIR = "target/es";
  private static final String clusterName = "cluster-name";

  public static void main(final String[] args) throws Exception {
    final Settings settings = Settings.builder()
      .put("path.home", ES_WORKING_DIR)
      .put("path.data", ES_WORKING_DIR + "/data")
      .put("path.logs", ES_WORKING_DIR + "/logs")
      .put("transport.type", "netty4")
      .put("http.type", "netty4")
      .put("cluster.name", clusterName)
      .put("http.port", HTTP_PORT)
      .put("transport.tcp.port", HTTP_TRANSPORT_PORT)
      .put("network.host", "127.0.0.1")
      .build();

    final Collection<Class<? extends Plugin>> classpathPlugins = Collections.singletonList(Netty4Plugin.class);
    try (final Node node = NodeFactory.makeNode(settings, classpathPlugins)) {
      node.start();
      runRestClient();
      runTransportClient();
    }

    TestUtil.checkSpan("java-elasticsearch", 6);
  }

  private static void runRestClient() throws IOException, InterruptedException {
    try (final RestClient restClient = RestClient.builder(new HttpHost("localhost", HTTP_PORT, "http")).build()) {
      final HttpEntity entity = new NStringEntity(
        "{\n" +
        "    \"user\": \"user\",\n" +
        "    \"post_date\": \"2009-11-15T14:12:12\",\n" +
        "    \"message\": \"trying out Elasticsearch\"\n" +
        "}", ContentType.APPLICATION_JSON);

      final Request request1 = new Request("PUT", "/twitter/tweet/1");
      request1.setEntity(entity);

      final Response indexResponse = restClient.performRequest(request1);
      System.out.println(indexResponse);

      final Request request2 = new Request("PUT", "/twitter/tweet/2");
      request2.setEntity(entity);

      final CountDownLatch latch = new CountDownLatch(1);
      restClient.performRequestAsync(request2, new ResponseListener() {
        @Override
        public void onSuccess(final Response response) {
          latch.countDown();
        }

        @Override
        public void onFailure(final Exception e) {
          latch.countDown();
        }
      });

      latch.await(30, TimeUnit.SECONDS);
    }
  }

  private static void runTransportClient() throws Exception {
    try (
      final PreBuiltTransportClient preBuiltClient = new PreBuiltTransportClient(Settings.builder().put("cluster.name", clusterName).build());
      final TransportClient client = preBuiltClient.addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), HTTP_TRANSPORT_PORT));
    ) {
      final IndexRequest indexRequest = new IndexRequest("twitter")
        .type("tweet")
        .id("1").
        source(XContentFactory.jsonBuilder()
          .startObject()
          .field("user", "user")
          .field("postDate", new Date())
          .field("message", "trying out Elasticsearch")
          .endObject());

      final IndexResponse indexResponse = client.index(indexRequest).actionGet();
      System.out.println(indexResponse);

      final CountDownLatch latch = new CountDownLatch(1);
      client.index(indexRequest, new ActionListener<IndexResponse>() {
        @Override
        public void onResponse(final IndexResponse indexResponse) {
          latch.countDown();
        }

        @Override
        public void onFailure(final Exception e) {
          latch.countDown();
        }
      });

      latch.await(30, TimeUnit.SECONDS);
    }
  }
}