package io.opentracing.contrib.instrumenter;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;

public class ForkTestNotifier extends EachTestNotifier {
  private final ObjectOutputStream out;
  private final Description description;

  public ForkTestNotifier(final RunNotifier notifier, final Description description, final ObjectOutputStream out) {
    super(notifier, description);
    this.description = description;
    this.out = out;
  }

  private void write(final Object obj) {
    try {
      System.err.println("Write: " + obj);
      out.writeObject(obj);
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void addFailure(final Throwable targetException) {
    write(new Response(description.getMethodName(), "addFailure", false, targetException));
  }

  @Override
  public void addFailedAssumption(final AssumptionViolatedException e) {
    write(new Response(description.getMethodName(), "addFailedAssumption", false, e));
  }

  @Override
  public void fireTestFinished() {
    write(new Response(description.getMethodName(), "fireTestFinished", true, null));
  }

  @Override
  public void fireTestStarted() {
    write(new Response(description.getMethodName(), "fireTestStarted", false, null));
  }

  @Override
  public void fireTestIgnored() {
    write(new Response(description.getMethodName(), "fireTestIgnored", true, null));
  }
}