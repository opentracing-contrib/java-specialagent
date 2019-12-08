package io.opentracing.contrib.specialagent.rule.method;

/**
 * ExampleMethodClass
 *
 * @author code98@163.com
 * @date 2019/11/20 1:25 上午
 */
public class ExampleMethodClass {

    public void test1() throws Exception {
        throw new Exception("test");
    }

    public String test2(String args) {
        return args;
    }
}
