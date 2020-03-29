package io.opentracing.contrib.specialagent.rule.dubbo27;

import io.opentracing.contrib.specialagent.rule.GreeterService;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;

public class MockClient {
    private ReferenceConfig<GreeterService> client;
    public MockClient(String ip, int port) {
        client = new ReferenceConfig<>();
        client.setApplication(new ApplicationConfig("test"));
        client.setInterface(GreeterService.class);
        client.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
//        client.setFilter("traceFilter");
        client.setUrl("dubbo://" + ip + ":" + port );
        client.setTimeout(10000);
        client.setCheck(false);
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
