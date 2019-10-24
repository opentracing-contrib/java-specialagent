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

package io.opentracing.contrib.specialagent.test.aws;

import java.util.concurrent.ThreadLocalRandom;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;

import io.opentracing.contrib.specialagent.TestUtil;

public class Aws1ITest {
  public static void main(final String[] args) throws Exception {
    System.getProperties().setProperty("sqlite4java.library.path", "src/test/resources/libs");

    final DynamoDBProxyServer server = ServerRunner.createServerFromCommandLineArgs(new String[] {"-inMemory", "-port", "8000"});
    server.start();

    final AmazonDynamoDB dbClient = buildClient();
    try {
      createTable(dbClient, "tableName-" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
    }
    catch (final Exception e) {
      System.out.println("Exception: " + e.getMessage() + "\nIgnoring.");
    }

    server.stop();
    dbClient.shutdown();
    TestUtil.checkSpan("java-aws-sdk", 1);
    System.exit(0);
  }

  private static AmazonDynamoDB buildClient() {
    final AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");
    final BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");
    final AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(endpointConfiguration)
      .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
      .withClientConfiguration(new ClientConfiguration().withConnectionTimeout(1));
    return builder.build();
  }

  private static void createTable(final AmazonDynamoDB dbClient, final String tableName) {
    final String partitionKeyName = tableName + "-pk";
    final CreateTableRequest createTableRequest = new CreateTableRequest()
      .withTableName(tableName)
      .withKeySchema(new KeySchemaElement().withAttributeName(partitionKeyName).withKeyType(KeyType.HASH))
      .withAttributeDefinitions(new AttributeDefinition().withAttributeName(partitionKeyName).withAttributeType("S"))
      .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(5L));
    dbClient.createTable(createTableRequest);
    System.out.println("Table " + tableName + " created");
  }
}