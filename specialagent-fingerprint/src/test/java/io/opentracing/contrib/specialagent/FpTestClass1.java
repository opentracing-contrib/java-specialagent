package io.opentracing.contrib.specialagent;

import io.opentracing.contrib.specialagent.FpTestClass2.Inner;

public class FpTestClass1 {
  public static Object foo = Inner.mutex;

  static {
    System.out.println(foo);
  }
}