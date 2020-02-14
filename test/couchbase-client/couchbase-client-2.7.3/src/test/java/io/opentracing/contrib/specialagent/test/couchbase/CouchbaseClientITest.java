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

package io.opentracing.contrib.specialagent.test.couchbase;

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.mock.BucketAlreadyExistsException;
import com.couchbase.mock.BucketConfiguration;
import com.couchbase.mock.CouchbaseMock;

import io.opentracing.contrib.specialagent.TestUtil;

public class CouchbaseClientITest {
  private static final String bucketName = "test";

  public static void main(final String[] args) throws BucketAlreadyExistsException, InterruptedException, IOException {
    final CouchbaseMock couchbaseMock = new CouchbaseMock("localhost", 8091, 2, 1);
    final BucketConfiguration bucketConfiguration = new BucketConfiguration();
    bucketConfiguration.name = bucketName;
    bucketConfiguration.numNodes = 1;
    bucketConfiguration.numReplicas = 1;
    bucketConfiguration.password = "";
    couchbaseMock.start();
    couchbaseMock.waitForStartup();
    couchbaseMock.createBucket(bucketConfiguration);

    final Cluster cluster = CouchbaseCluster.create(DefaultCouchbaseEnvironment.builder().connectTimeout(TimeUnit.SECONDS.toMillis(60)).build());
    final Bucket bucket = cluster.openBucket(bucketName);

    final JsonObject arthur = JsonObject
      .create().put("name", "Arthur")
      .put("email", "kingarthur@couchbase.com")
      .put("interests", JsonArray.from("Holy Grail", "African Swallows"));

    bucket.upsert(JsonDocument.create("u:king_arthur", arthur));

    System.out.println(bucket.get("u:king_arthur"));

    cluster.disconnect(60, TimeUnit.SECONDS);
    couchbaseMock.stop();

    TestUtil.checkSpan(new ComponentSpanCount("couchbase-java-client.*", 2));
  }
}