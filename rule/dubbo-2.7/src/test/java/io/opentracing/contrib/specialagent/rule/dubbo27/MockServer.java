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

package io.opentracing.contrib.specialagent.rule.dubbo27;

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;

import io.opentracing.contrib.specialagent.TestUtil;

public class MockServer {
  final ServiceConfig<GreeterService> service;
  final String linkLocalIp;

  MockServer() {
    linkLocalIp = NetUtils.getLocalAddress().getHostAddress();
    if (linkLocalIp != null) {
      // avoid dubbo's logic which might pick docker ip
      System.setProperty(Constants.DUBBO_IP_TO_BIND, linkLocalIp);
      System.setProperty(Constants.DUBBO_IP_TO_REGISTRY, linkLocalIp);
    }

    service = new ServiceConfig<>();
    service.setApplication(new ApplicationConfig("test"));
    service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
    service.setProtocol(new ProtocolConfig("dubbo", TestUtil.nextFreePort()));
    service.setInterface(GreeterService.class);
    service.setRef(new GreeterServiceImpl());
  }

  void start() {
    service.export();
  }

  void stop() {
    service.unexport();
  }

  int port() {
    return service.getProtocol().getPort();
  }

  String ip() {
    return linkLocalIp != null ? linkLocalIp : "127.0.0.1";
  }
}