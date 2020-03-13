package io.opentracing.contrib.specialagent.rule.dubbo26;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import io.opentracing.contrib.specialagent.rule.GreeterService;


public class MockClient {
    private ReferenceConfig<GreeterService> client;
    public MockClient(String ip, int port) {
        client = new ReferenceConfig<>();
        client.setApplication(new ApplicationConfig("test"));
        client.setFilter("traceFilter");
        client.setInterface(GreeterService.class);
        client.setUrl("dubbo://" + ip + ":" + port + "?scope=remote");
    }

    public GreeterService get() {
        return client.get();
    }

    void stop() {
        client.destroy();
    }
}
