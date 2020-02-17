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

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.mongodb.Block;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ClusterSettings.Builder;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.opentracing.contrib.specialagent.TestUtil;

@SuppressWarnings("deprecation")
public class MongoAsyncITest {
  public static void main(final String[] args) throws Exception {
    final MongoServer server = new MongoServer(new MemoryBackend());
    final InetSocketAddress serverAddress = server.bind();

    try (final MongoClient client = createAsyncClient(serverAddress)) {
      final MongoDatabase database = client.getDatabase("myMongoDb");
      if (database.getCollection("customers") == null) {
        database.createCollection("customers", new SingleResultCallback<Void>() {
          @Override
          public void onResult(final Void result, final Throwable t) {
            System.out.println("created");
          }
        });
      }

      final MongoCollection<Document> collection = database.getCollection("customers");
      final Document document = new Document();
      document.put("name", "Name");
      document.put("company", "Company");
      collection.insertOne(document, new SingleResultCallback<Void>() {
        @Override
        public void onResult(final Void result, final Throwable t) {
          System.out.println("result");
        }
      });

      TimeUnit.SECONDS.sleep(10);
    }

    server.shutdownNow();

    TestUtil.checkSpan(new ComponentSpanCount("java-mongo", 1));
  }

  private static MongoClient createAsyncClient(final InetSocketAddress serverAddress) {
    final MongoClientSettings clientSettings = MongoClientSettings.builder().applyToClusterSettings(new Block<Builder>() {
      @Override
      public void apply(final ClusterSettings.Builder builder) {
        builder.hosts(Arrays.asList(new ServerAddress(serverAddress)));
      }
    }).build();

    return MongoClients.create(clientSettings);

    // MongoClientSettings settings = MongoClientSettings.builder()
    // .applyConnectionString(
    // new ConnectionString("mongodb://localhost:27017"))
    // .build();
    //
    //
    // return
    // MongoClients.create(MongoClientSettings.builder(settings).build());
  }
}