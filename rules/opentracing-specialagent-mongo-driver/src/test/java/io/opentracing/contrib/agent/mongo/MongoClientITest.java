/*
 * Copyright 2018 The OpenTracing Authors
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.agent.mongo;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.mongodb.Block;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.connection.ClusterSettings;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author rmfitzpatrick
 * @author Seva Safris
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(debug=true, verbose=true)
public class MongoClientITest {
  @Test
  public void testMongoClient(final MockTracer tracer) {
    final MongoServer server = new MongoServer(new MemoryBackend());
    final InetSocketAddress serverAddress = server.bind();

    try {
      MongoClientSettings mongoSettings = MongoClientSettings.builder().applyToClusterSettings(new Block<ClusterSettings.Builder>() {
        @Override
        public void apply(final ClusterSettings.Builder builder) {
          builder.hosts(Arrays.asList(new ServerAddress(serverAddress)));
        }
      }).build();

      final MongoClient mongoClient = MongoClients.create(mongoSettings);

      final MongoCollection<Document> collection = mongoClient.getDatabase("MyDB").getCollection("MyCollection");
      final Document myDocument = new Document("name", "MyDocument");
      collection.insertOne(myDocument);
      collection.find().first();
      mongoClient.close();

      final List<MockSpan> spans = tracer.finishedSpans();
      assertEquals(2, spans.size());
      assertEquals("insert", spans.get(0).operationName());
      assertEquals("find", spans.get(1).operationName());
    }
    finally {
      server.shutdown();
    }
  }
}