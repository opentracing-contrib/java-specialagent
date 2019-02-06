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
package io.opentracing.contrib.specialagent.aws;

import static org.junit.Assert.assertEquals;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config(verbose = true)
public class AwsTest {

  @Test
  public void testSyncClient(final MockTracer tracer) {
    tracer.reset();
    try {
      AmazonDynamoDB dbClient = buildClient();
      createTable(dbClient, "table-1");
    } catch (Exception ignore) {
    }

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());
  }

  @Test
  public void testAsyncClient(final MockTracer tracer) {
    tracer.reset();

    try {
      AmazonDynamoDBAsync dbClient = buildAsyncClient();
      Future<CreateTableResult> createTableResultFuture = createTableAsync(dbClient,
          "asyncRequest");
      CreateTableResult result = createTableResultFuture.get(10, TimeUnit.SECONDS);
      assertEquals("asyncRequest", result.getTableDescription().getTableName());
    } catch (Exception ignore) {
    }

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());
  }

  private AmazonDynamoDB buildClient() {
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");

    BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");

    return AmazonDynamoDBClientBuilder.standard()
        .withEndpointConfiguration(endpointConfiguration)
        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
        .withClientConfiguration(new ClientConfiguration().withConnectionTimeout(1))
        .build();
  }

  private AmazonDynamoDBAsync buildAsyncClient() {
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");

    BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");

    return AmazonDynamoDBAsyncClientBuilder.standard()
        .withEndpointConfiguration(endpointConfiguration).withRequestHandlers()
        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
        .withClientConfiguration(new ClientConfiguration().withConnectionTimeout(1))
        .build();
  }

  private void createTable(AmazonDynamoDB dbClient, String tableName) {
    String partitionKeyName = tableName + "Id";

    try {
      CreateTableRequest createTableRequest = new CreateTableRequest()
          .withTableName(tableName)
          .withKeySchema(new KeySchemaElement()
              .withAttributeName(partitionKeyName)
              .withKeyType(KeyType.HASH))
          .withAttributeDefinitions(new AttributeDefinition()
              .withAttributeName(partitionKeyName).withAttributeType("S"))
          .withProvisionedThroughput(new ProvisionedThroughput()
              .withReadCapacityUnits(10L)
              .withWriteCapacityUnits(5L));

      dbClient.createTable(createTableRequest);
    } catch (Exception ignore) {
    }
  }

  private Future<CreateTableResult> createTableAsync(AmazonDynamoDBAsync dbClient,
      String tableName) {
    String partitionKeyName = tableName + "Id";

    CreateTableRequest createTableRequest = new CreateTableRequest()
        .withTableName(tableName)
        .withKeySchema(new KeySchemaElement()
            .withAttributeName(partitionKeyName)
            .withKeyType(KeyType.HASH))
        .withAttributeDefinitions(new AttributeDefinition()
            .withAttributeName(partitionKeyName).withAttributeType("S"))
        .withProvisionedThroughput(new ProvisionedThroughput()
            .withReadCapacityUnits(10L)
            .withWriteCapacityUnits(5L));

    return dbClient.createTableAsync(createTableRequest,
        new AsyncHandler<CreateTableRequest, CreateTableResult>() {
          @Override
          public void onError(Exception ignore) {
          }

          @Override
          public void onSuccess(CreateTableRequest request, CreateTableResult createTableResult) {
          }
        });
  }
}