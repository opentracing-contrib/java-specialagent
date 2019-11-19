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

package io.opentracing.contrib.specialagent.rule.elasticsearch.rest;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class ElasticsearchRestClientTest {
  private static final int HTTP_PORT = 9205;
  private static final String HTTP_TRANSPORT_PORT = "9305";
  private static final String ES_WORKING_DIR = "target/es";
  private static final String clusterName = "cluster-name";
  private static Node node;

  @BeforeClass
  public static void startElasticsearch() throws Exception {
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
    final Collection<Class<? extends Plugin>> plugins = Collections.singletonList(Netty4Plugin.class);
    node = new PluginConfigurableNode(settings, plugins);
    node.start();
  }

  @AfterClass
  public static void stopElasticsearch() throws Exception {
    if (node != null)
      node.close();
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  private static void test1(final RestClient client, final HttpEntity entity) throws IOException {
    final Request request = new Request("PUT", "/twitter/tweet/1");
    request.setEntity(entity);

    final Response indexResponse = client.performRequest(request);
    assertNotNull(indexResponse);
  }

  private static void test2(final RestClient client, final HttpEntity entity) {
    final Request request = new Request("PUT", "/twitter/tweet/2");
    request.setEntity(entity);

    client.performRequestAsync(request, new ResponseListener() {
      @Override
      public void onSuccess(final Response response) {
      }

      @Override
      public void onFailure(final Exception exception) {
      }
    });
  }

  @Test
  public void restClient(final MockTracer tracer) throws IOException {
    try (final RestClient client = RestClient.builder(new HttpHost("localhost", HTTP_PORT, "http")).build()) {
      final HttpEntity entity = new NStringEntity(
        "{\n" +
        "    \"user\" : \"kimchy\",\n" +
        "    \"post_date\" : \"2009-11-15T14:12:12\",\n" +
        "    \"message\" : \"trying out Elasticsearch\"\n" +
        "}", ContentType.APPLICATION_JSON);

      test1(client, entity);
      test2(client, entity);

      await().atMost(15, TimeUnit.SECONDS).until(new Callable<Integer>() {
        @Override
        public Integer call() {
          return tracer.finishedSpans().size();
        }
      }, equalTo(2));
    }

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }

  @Test
  public void restClientWithCallback(final MockTracer tracer) throws IOException {
    AtomicInteger counter = new AtomicInteger();
    try (final RestClient client = RestClient.builder(new HttpHost("localhost", HTTP_PORT, "http"))
        .setHttpClientConfigCallback(new HttpClientConfigCallback() {
          @Override
          public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
            counter.incrementAndGet();
            return httpClientBuilder;
          }
        }).build()) {
      final HttpEntity entity = new NStringEntity(
          "{\n" +
              "    \"user\" : \"kimchy\",\n" +
              "    \"post_date\" : \"2009-11-15T14:12:12\",\n" +
              "    \"message\" : \"trying out Elasticsearch\"\n" +
              "}", ContentType.APPLICATION_JSON);

      test1(client, entity);
      test2(client, entity);

      await().atMost(15, TimeUnit.SECONDS).until(new Callable<Integer>() {
        @Override
        public Integer call() {
          return tracer.finishedSpans().size();
        }
      }, equalTo(2));

      assertEquals(1, counter.get());
    }

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }

  private static class PluginConfigurableNode extends Node {
    public PluginConfigurableNode(final Settings settings, final Collection<Class<? extends Plugin>> classpathPlugins) {
      super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins);
    }
  }
}