package io.opentracing.contrib.specialagent;

import javax.el.ELContextEvent;
import javax.el.ELException;
import javax.el.ResourceBundleELResolver;

import org.hamcrest.Factory;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.matchers.JUnitMatchers;
import org.junit.runner.notification.RunListener;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.RunnerScheduler;
import org.objectweb.asm.ClassVisitor;

@Ignore("class annotation dep")
public class FpTestClass2 extends org.junit.Assert implements RunnerScheduler {
  static class Inner<T extends ParentRunner<? super javax.el.ELContextListener>> extends RunListener implements javax.el.ELContextListener {
    public static Object mutex = new Object();
    public static final long serialVersionUID = 7264913920703685863L;

    static Object staticRef = ResourceBundleELResolver.RESOLVABLE_AT_DESIGN_TIME;
    static Object staticRefMethod = org.junit.experimental.ParallelComputer.classes();

    static Object staticMethod(@SuppressWarnings("unused") final Assume assume) {
      return new Assume();
    }

    @SuppressWarnings("unused")
    private Number memberFieldJre;

    private ParameterSignature memberFieldDep;

    @Test
    @Override
    public void contextCreated(ELContextEvent ece) {
      final Object x = (ELContextEvent)null;
    }
  }

  class MemberInner {
  }

  @Parameter
  private static DataPoint staticFieldDep;

  private static Object bar = Inner.serialVersionUID;

  Object staticRef1 = javax.el.StaticFieldELResolver.RESOLVABLE_AT_DESIGN_TIME;

  @Factory
  @SuppressWarnings("unused")
   public Object instanceMethod(final JUnitMatchers type) throws javax.el.PropertyNotFoundException {
    return new org.junit.runner.notification.RunNotifier();
  }

  @Override
  public void schedule(Runnable childStatement) {
    @Deprecated
    @SuppressWarnings("unused")
    ClassVisitor classVisitor = null;
    classVisitor = null;
    final Exception e = new ELException();
  }

  @Override
  public void finished() {
  }
}