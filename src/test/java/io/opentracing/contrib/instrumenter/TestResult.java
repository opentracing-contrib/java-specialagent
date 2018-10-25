package io.opentracing.contrib.instrumenter;

import java.io.Serializable;

class TestResult implements Serializable {
  private static final long serialVersionUID = -5513350610214154919L;

  private final String methodName;
  private final Throwable targetException;

  public TestResult(final String methodName, final Throwable targetException) {
    this.methodName = methodName;
    this.targetException = targetException;
  }

  public String getMethodName() {
    return this.methodName;
  }

  public Throwable getTargetException() {
    return this.targetException;
  }

  @Override
  public String toString() {
    return "{\"methodName\": \"" + methodName + "\", \"targetException\": " + (targetException == null ? "null" : targetException.getClass().getName()) + "}";
  }
}