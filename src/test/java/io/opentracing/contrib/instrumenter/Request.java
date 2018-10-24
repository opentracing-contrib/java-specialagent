package io.opentracing.contrib.instrumenter;

import java.io.Serializable;
import java.util.Arrays;

class Request implements Serializable {
  private static final long serialVersionUID = -8573847624477323355L;

  private final String methodName;
  private final Class<?>[] parameterTypes;

  public Request(final String methodName, final Class<?> ... parameterTypes) {
    this.methodName = methodName;
    this.parameterTypes = parameterTypes;
  }

  public String getMethodName() {
    return this.methodName;
  }

  public Class<?>[] getParameterTypes() {
    return this.parameterTypes;
  }

  @Override
  public String toString() {
    return "{\"methodName\": \"" + methodName + "\", \"parameterTypes\": " + (parameterTypes == null ? "null" : Arrays.toString(parameterTypes)) + "}";
  }
}