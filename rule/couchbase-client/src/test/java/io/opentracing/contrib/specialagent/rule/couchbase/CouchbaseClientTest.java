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

package io.opentracing.contrib.specialagent.rule.couchbase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.mock.BucketConfiguration;
import com.couchbase.mock.CouchbaseMock;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
public class CouchbaseClientTest {
  private static final String bucketName = "test";
  private static CouchbaseMock couchbaseMock;

  @BeforeClass
  public static void startCouchbaseMock() throws Exception {
    couchbaseMock = new CouchbaseMock("localhost", 8091, 2, 1);
    BucketConfiguration bucketConfiguration = new BucketConfiguration();
    bucketConfiguration.name = bucketName;
    bucketConfiguration.numNodes = 1;
    bucketConfiguration.numReplicas = 1;
    bucketConfiguration.password="";
    couchbaseMock.start();
    couchbaseMock.waitForStartup();
    couchbaseMock.createBucket(bucketConfiguration);
  }

  @AfterClass
  public static void stopCouchbaseMock() {
    if(couchbaseMock != null)
      couchbaseMock.stop();
  }

  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void test(final MockTracer tracer) {
    final Cluster cluster = CouchbaseCluster.create(DefaultCouchbaseEnvironment.builder()
        .connectTimeout(TimeUnit.SECONDS.toMillis(60)).build());

    final Bucket bucket = cluster.openBucket(bucketName);

    final JsonObject arthur = JsonObject.create()
        .put("name", "Arthur")
        .put("email", "kingarthur@couchbase.com")
        .put("interests", JsonArray.from("Holy Grail", "African Swallows"));

    bucket.upsert(JsonDocument.create("u:king_arthur", arthur));

    System.out.println(bucket.get("u:king_arthur"));

    cluster.disconnect(60, TimeUnit.SECONDS);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(6, spans.size());

    boolean foundCouchbaseSpan = false;
    for (MockSpan span : spans) {
      final String component = (String) span.tags().get(Tags.COMPONENT.getKey());
      if(component != null && component.startsWith("couchbase-java-client")) {
        foundCouchbaseSpan = true;
        break;
      }
    }

    assertTrue("couchbase-java-client span not found", foundCouchbaseSpan);
  }
}