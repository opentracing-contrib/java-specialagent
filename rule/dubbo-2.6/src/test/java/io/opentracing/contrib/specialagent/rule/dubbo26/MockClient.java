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

package io.opentracing.contrib.specialagent.rule.dubbo26;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;

public class MockClient {
  private final ReferenceConfig<GreeterService> client;

  public MockClient(final String ip, final int port) {
    client = new ReferenceConfig<>();
    client.setApplication(new ApplicationConfig("test"));
    client.setInterface(GreeterService.class);
    client.setUrl("dubbo://" + ip + ":" + port + "?scope=remote");
    client.setScope("local");
    client.setInjvm(true);
  }

  public GreeterService get() {
    return client.get();
  }

  void stop() {
    client.destroy();
  }
}