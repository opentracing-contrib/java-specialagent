package io.opentracing.contrib.specialagent.elasticsearch;

import static org.awaitility.Awaitility.await;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class Elasticsearch6Test {
  private static final int HTTP_PORT = 9205;
  private static final String HTTP_TRANSPORT_PORT = "9305";
  private static final String ES_WORKING_DIR = "target/es";
  private static String clusterName = "cluster-name";
  private static Node node;

  @BeforeClass
  public static void startElasticsearch() throws Exception {
    Settings settings = Settings.builder()
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
    Collection plugins = Collections.singletonList(Netty4Plugin.class);
    node = new PluginConfigurableNode(settings, plugins);
    node.start();
  }

  @AfterClass
  public static void stopElasticsearch() throws Exception {
    if (node != null) {
      node.close();
    }
  }

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void restClient(final MockTracer tracer) throws Exception {
    RestClient restClient = RestClient.builder(
        new HttpHost("localhost", HTTP_PORT, "http"))
        .build();

    HttpEntity entity = new NStringEntity(
        "{\n" +
            "    \"user\" : \"kimchy\",\n" +
            "    \"post_date\" : \"2009-11-15T14:12:12\",\n" +
            "    \"message\" : \"trying out Elasticsearch\"\n" +
            "}", ContentType.APPLICATION_JSON);

    Request request = new Request("PUT", "/twitter/tweet/1");
    request.setEntity(entity);

    Response indexResponse = restClient.performRequest(request);

    assertNotNull(indexResponse);

    request = new Request("PUT", "/twitter/tweet/2");
    request.setEntity(entity);

    restClient
        .performRequestAsync(request, new ResponseListener() {
          @Override
          public void onSuccess(Response response) {
          }

          @Override
          public void onFailure(Exception exception) {
          }
        });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    restClient.close();

    List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }

  @Test
  public void transportClient(MockTracer tracer) throws Exception {

    Settings settings = Settings.builder()
        .put("cluster.name", clusterName).build();

    TransportClient client = new PreBuiltTransportClient(settings)
        .addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"),
            Integer.parseInt(HTTP_TRANSPORT_PORT)));

    IndexRequest indexRequest = new IndexRequest("twitter").type("tweet").id("1").
        source(jsonBuilder()
            .startObject()
            .field("user", "kimchy")
            .field("postDate", new Date())
            .field("message", "trying out Elasticsearch")
            .endObject()
        );

    IndexResponse indexResponse = client.index(indexRequest).actionGet();
    assertNotNull(indexResponse);

    client.index(indexRequest, new ActionListener<IndexResponse>() {
      @Override
      public void onResponse(IndexResponse indexResponse) {
      }

      @Override
      public void onFailure(Exception e) {
      }
    });

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    client.close();

    List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }

  private static class PluginConfigurableNode extends Node {

    public PluginConfigurableNode(Settings settings,
        Collection<Class<? extends Plugin>> classpathPlugins) {
      super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins, true);
    }

    @Override
    protected void registerDerivedNodeNameWithLogger(String s) {

    }
  }

}
