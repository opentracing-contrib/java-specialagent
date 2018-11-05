package io.opentracing.contrib.specialagent;

abstract class NamedDigest<T extends NamedDigest<T>> extends Digest implements Comparable<T> {
  private static final long serialVersionUID = 3682401024183679159L;

  private final String name;

  NamedDigest(final String name) {
    this.name = name;
  }

  public final String getName() {
    return name;
  }

  @Override
  public final int compareTo(final T o) {
    return getName().compareTo(o.getName());
  }
}