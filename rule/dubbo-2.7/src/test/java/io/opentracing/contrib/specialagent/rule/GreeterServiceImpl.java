package io.opentracing.contrib.specialagent.rule;

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

    @Override
    public String sayGoodbye(String name) {
        return "goodbye " + name;
    }


}
