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

package io.opentracing.contrib.specialagent.aws2;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

@RunWith(AgentRunner.class)
public class Aws2Test {
  @Before
  public void before(final MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void testSyncClient(final MockTracer tracer) {
    final DynamoDbClient dbClient = buildClient();
    try {
      createTable(dbClient, "table-1");
    }
    catch (final Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(1));
    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());
  }

  @Test
  public void testAsyncClient(final MockTracer tracer) throws Exception {
    final DynamoDbAsyncClient dbClient = buildAsyncClient();
    final CompletableFuture<CreateTableResponse> createTableResultFuture = createTableAsync(dbClient, "asyncRequest");
    try {
      final CreateTableResponse result = createTableResultFuture.get(10, TimeUnit.SECONDS);
      // The following assertion is only relevant when a local instance of dynamodb is present.
      // If a local instance of dynamodb is NOT present, an exception is thrown.
      assertEquals("asyncRequest", result.tableDescription().tableName());
    }
    catch (final Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(() -> tracer.finishedSpans().size(), equalTo(1));
    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());
  }

  private static DynamoDbClient buildClient() {
    final AwsSessionCredentials awsCreds = AwsSessionCredentials.create("access_key_id", "secret_key_id", "session_token");
    return DynamoDbClient.builder()
      .endpointOverride(URI.create("http://localhost:8000"))
      .region(Region.US_WEST_2)
      .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
      .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(1)).build())
      .build();
  }

  private static DynamoDbAsyncClient buildAsyncClient() {
    final AwsSessionCredentials awsCreds = AwsSessionCredentials.create("access_key_id", "secret_key_id", "session_token");
    final DynamoDbAsyncClient build = DynamoDbAsyncClient.builder()
      .endpointOverride(URI.create("http://localhost:8000"))
      .region(Region.US_WEST_2)
      .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
      .overrideConfiguration(ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(1)).build())
      .build();
    return build;
  }

  private static void createTable(final DynamoDbClient dbClient, final String tableName) {
    final String partitionKeyName = tableName + "Id";
    final CreateTableRequest createTableRequest = CreateTableRequest.builder()
      .tableName(tableName)
      .keySchema(KeySchemaElement.builder().attributeName(partitionKeyName).keyType(KeyType.HASH).build())
      .attributeDefinitions(AttributeDefinition.builder().attributeName(partitionKeyName).attributeType("S").build())
      .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(5L).build()).build();

    dbClient.createTable(createTableRequest);
  }

  private static CompletableFuture<CreateTableResponse> createTableAsync(final DynamoDbAsyncClient dbClient, final String tableName) {
    final String partitionKeyName = tableName + "Id";
    final CreateTableRequest createTableRequest = CreateTableRequest.builder()
      .tableName(tableName).keySchema(KeySchemaElement.builder().attributeName(partitionKeyName).keyType(KeyType.HASH).build())
      .attributeDefinitions(AttributeDefinition.builder().attributeName(partitionKeyName).attributeType("S").build())
      .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(5L).build()).build();

    return dbClient.createTable(createTableRequest);
  }
}