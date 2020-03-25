package io.opentracing.contrib.specialagent.test.dubbo26;

public class GreeterServiceImpl implements GreeterService {

    public static volatile boolean isThrowExecption = false;

    public static final String errorMesg = "fail to call GreeterService";
    @Override
    public String sayHello(String name) {
        if (isThrowExecption) {
            throw new RuntimeException(errorMesg);
        }
        return "hello " + name;
    }

}
