package io.opentracing.contrib.specialagent;

public enum Instrumenter {
  BYTEMAN(new BytemanTransformer()),
  BYTEBUDDY(new ByteBuddyTransformer());

  final Transformer transformer;

  Instrumenter(final Transformer transformer) {
    this.transformer = transformer;
  }
}