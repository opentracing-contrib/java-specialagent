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

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;
import org.junit.runner.RunWith;

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

@RunWith(AgentRunner.class)
public class AwsTest {
  private static final Logger logger = Logger.getLogger(AwsTest.class.getName());

  @Test
  public void testSyncClient(final MockTracer tracer) {
    tracer.reset();
    final AmazonDynamoDB dbClient = buildClient();
    try {
      createTable(dbClient, "table-1");
    }
    catch (final Exception e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());
  }

  @Test
  public void testAsyncClient(final MockTracer tracer) throws Exception {
    tracer.reset();

    final AmazonDynamoDBAsync dbClient = buildAsyncClient();
    final Future<CreateTableResult> createTableResultFuture = createTableAsync(dbClient, "asyncRequest");
    try {
      final CreateTableResult result = createTableResultFuture.get(10, TimeUnit.SECONDS);
      assertEquals("asyncRequest", result.getTableDescription().getTableName()); // FIXME: This assertion is not happening due to the exception thrown from the previous line
    }
    catch (final Exception e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());
  }

  private static AmazonDynamoDB buildClient() {
    final AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");
    final BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");
    return AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(endpointConfiguration)
      .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
      .withClientConfiguration(new ClientConfiguration().withConnectionTimeout(1))
      .build();
  }

  private static AmazonDynamoDBAsync buildAsyncClient() {
    final AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");
    final BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");
    return AmazonDynamoDBAsyncClientBuilder
      .standard()
      .withEndpointConfiguration(endpointConfiguration)
      .withRequestHandlers()
      .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
      .withClientConfiguration(new ClientConfiguration().withConnectionTimeout(1))
      .build();
  }

  private static void createTable(final AmazonDynamoDB dbClient, final String tableName) {
    final String partitionKeyName = tableName + "Id";
    final CreateTableRequest createTableRequest = new CreateTableRequest()
      .withTableName(tableName)
      .withKeySchema(new KeySchemaElement().withAttributeName(partitionKeyName).withKeyType(KeyType.HASH))
      .withAttributeDefinitions(new AttributeDefinition().withAttributeName(partitionKeyName).withAttributeType("S"))
      .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(5L));

    dbClient.createTable(createTableRequest);
  }

  private static Future<CreateTableResult> createTableAsync(final AmazonDynamoDBAsync dbClient, final String tableName) {
    final String partitionKeyName = tableName + "Id";
    final CreateTableRequest createTableRequest = new CreateTableRequest()
      .withTableName(tableName).withKeySchema(new KeySchemaElement().withAttributeName(partitionKeyName).withKeyType(KeyType.HASH))
      .withAttributeDefinitions(new AttributeDefinition().withAttributeName(partitionKeyName).withAttributeType("S"))
      .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(5L));

    return dbClient.createTableAsync(createTableRequest, new AsyncHandler<CreateTableRequest,CreateTableResult>() {
      @Override
      public void onError(final Exception exception) {
      }

      @Override
      public void onSuccess(final CreateTableRequest request, final CreateTableResult createTableResult) {
      }
    });
  }
}