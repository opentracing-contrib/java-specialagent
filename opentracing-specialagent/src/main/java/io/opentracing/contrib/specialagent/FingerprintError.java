package io.opentracing.contrib.specialagent;

class FingerprintError {
  public static enum Reason {
    MISSING,
    MISMATCH
  }

  private final Reason reason;
  private final ClassFingerprint a;
  private final ClassFingerprint b;

  FingerprintError(final Reason reason, final ClassFingerprint a, final ClassFingerprint b) {
    this.reason = reason;
    this.a = a;
    this.b = b;
  }

  @Override
  public String toString() {
    return reason == Reason.MISSING ? " " + reason + " " + a.getName() : (reason + " " + a.getName() + " (a <> b):\n(a) " + a.toString().replace("\n", "\n    ") + "\n(b) " + b.toString().replace("\n", "\n    "));
  }
}