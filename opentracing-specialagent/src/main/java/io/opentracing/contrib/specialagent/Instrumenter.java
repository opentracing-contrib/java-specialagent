package io.opentracing.contrib.specialagent;

public enum Instrumenter {
  BYTEMAN(new BytemanManager()),
  BYTEBUDDY(new ByteBuddyManager());

  final Manager manager;

  Instrumenter(final Manager manager) {
    this.manager = manager;
  }
}