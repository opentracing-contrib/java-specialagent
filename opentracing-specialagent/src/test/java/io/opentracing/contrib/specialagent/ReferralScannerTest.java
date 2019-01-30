package io.opentracing.contrib.specialagent;

import java.io.IOException;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import io.opentracing.contrib.specialagent.ReferralScanner.Manifest;

public class ReferralScannerTest {
  private Type memberField = Type.BOOLEAN_TYPE;
  private Type memberMethod = Type.getType(ReferralScannerTest.class);

  static {
    final int expandFrames = ClassReader.EXPAND_FRAMES;
    if (expandFrames == -1)
      throw new ExceptionInInitializerError();
  }

  @Test
  public void test() throws IOException {
    final Manifest referrals = new Manifest();
    new ReferralScanner(referrals).scanReferrals(ClassLoader.getSystemClassLoader(), getClass().getName().replace('.', '/').concat(".class"));
  }
}