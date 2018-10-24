package io.opentracing.contrib.instrumenter;

import java.io.Serializable;

class Response implements Serializable {
  private static final long serialVersionUID = -5513350610214154919L;

  private final String methodName;
  private final String notifierMethod;
  private final boolean terminal;
  private final Throwable targetException;

  public Response(final String methodName, final String notifierMethod, final boolean terminal, final Throwable targetException) {
    this.methodName = methodName;
    this.notifierMethod = notifierMethod;
    this.terminal = terminal;
    this.targetException = targetException;
  }

  public String getMethodName() {
    return this.methodName;
  }

  public String getNotifierMethod() {
    return this.notifierMethod;
  }

  public boolean isTerminal() {
    return this.terminal;
  }

  public Throwable getTargetException() {
    return this.targetException;
  }

  @Override
  public String toString() {
    return "{\"methodName\": \"" + methodName + "\", \"notifierMethod\": \"" + notifierMethod + "\", \"terminal\": " + terminal + ", \"targetException\": " + (targetException == null ? "null" : targetException.getClass().getName()) + "}";
  }
}