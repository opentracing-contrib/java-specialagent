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

package io.opentracing.contrib.specialagent.test.dubbo27;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.config.*;
import org.apache.dubbo.common.utils.NetUtils;

public class DubboITest {
  public static void main(final String[] args) throws Exception {
    final CountDownLatch latch = TestUtil.initExpectedSpanLatch(2);
    String linkLocalIp = "127.0.0.1";
    ServiceConfig<GreeterService> service = getService();
    service.export();
    ReferenceConfig<GreeterService> client =getClient(linkLocalIp, service.getProtocol().getPort());
    client.get().sayHello("jorge");
    TestUtil.checkSpan(latch, new ComponentSpanCount("java-dubbo", 2) );
    client.destroy();
    service.unexport();
  }

  private static ServiceConfig<GreeterService>  getService() {
    ServiceConfig<GreeterService> service = new ServiceConfig<>();
    service.setApplication(new ApplicationConfig("test"));
    service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
    service.setProtocol(new ProtocolConfig("dubbo", TestUtil.nextFreePort()));
    service.setInterface(GreeterService.class);
    service.setRef(new GreeterServiceImpl());
    return service;
  }


  private static ReferenceConfig<GreeterService> getClient(String ip, int port) {
    ReferenceConfig<GreeterService> client = new ReferenceConfig<>();
    client.setApplication(new ApplicationConfig("test"));
    client.setInterface(GreeterService.class);
    client.setUrl("dubbo://" + ip + ":" + port );
    return client;
  }

}