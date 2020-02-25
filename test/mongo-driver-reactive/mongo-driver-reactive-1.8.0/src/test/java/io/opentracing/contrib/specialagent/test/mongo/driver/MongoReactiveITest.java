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

package io.opentracing.contrib.specialagent.test.mongo.driver;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.mongodb.Block;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ClusterSettings.Builder;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.Success;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;

public class MongoReactiveITest {
  public static void main(final String[] args) throws InterruptedException {
    final MongoServer server = new MongoServer(new MemoryBackend());
    final InetSocketAddress serverAddress = server.bind();
    final MongoClientSettings mongoSettings = MongoClientSettings.builder().applyToClusterSettings(new Block<Builder>() {
      @Override
      public void apply(final ClusterSettings.Builder builder) {
        builder.hosts(Arrays.asList(new ServerAddress(serverAddress)));
      }
    }).build();

    try (final MongoClient mongoClient = MongoClients.create(mongoSettings)) {
      final CountDownLatch latch = new CountDownLatch(1);
      final MongoCollection<Document> collection = mongoClient.getDatabase("MyDB").getCollection("MyCollection");
      final Document myDocument = new Document("name", "MyDocument");
      collection.insertOne(myDocument).subscribe(new Subscriber<Success>() {
        @Override
        public void onSubscribe(final Subscription s) {
          s.request(1);
        }

        @Override
        public void onNext(final Success success) {
        }

        @Override
        public void onError(final Throwable t) {
        }

        @Override
        public void onComplete() {
          latch.countDown();
        }
      });

      latch.await(15, TimeUnit.SECONDS);
      TimeUnit.SECONDS.sleep(10);
    }

    server.shutdownNow();
    TestUtil.checkSpan(new ComponentSpanCount("java-mongo", 1));
  }
}