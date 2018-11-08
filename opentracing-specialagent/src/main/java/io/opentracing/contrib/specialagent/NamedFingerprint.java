package io.opentracing.contrib.specialagent;

abstract class NamedFingerprint<T extends NamedFingerprint<T>> extends Fingerprint implements Comparable<T> {
  private static final long serialVersionUID = 3682401024183679159L;

  private final String name;

  NamedFingerprint(final String name) {
    this.name = name;
  }

  public final String getName() {
    return name;
  }

  @Override
  public int compareTo(final T o) {
    return getName().compareTo(o.getName());
  }
}