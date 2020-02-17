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

import org.bson.Document;

import com.mongodb.Block;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ClusterSettings.Builder;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.opentracing.contrib.specialagent.TestUtil;

public class MongoITest {
  public static void main(final String[] args) throws Exception {
    final MongoServer server = new MongoServer(new MemoryBackend());
    final InetSocketAddress serverAddress = server.bind();

    final MongoClientSettings mongoSettings = MongoClientSettings.builder().applyToClusterSettings(new Block<Builder>() {
      @Override
      public void apply(final ClusterSettings.Builder builder) {
        builder.hosts(Arrays.asList(new ServerAddress(serverAddress)));
      }
    }).build();

    try (final com.mongodb.client.MongoClient client = MongoClients.create(mongoSettings)) {
      final MongoDatabase database = client.getDatabase("myMongoDb");
      if (database.getCollection("customers") == null)
        database.createCollection("customers");

      final MongoCollection<Document> collection = database.getCollection("customers");
      final Document document = new Document();
      document.put("name", "Name");
      document.put("company", "Company");
      collection.insertOne(document);
      collection.find().first();
    }

    server.shutdownNow();
    TestUtil.checkSpan(new ComponentSpanCount("java-mongo", 2));
  }
}