package io.opentracing.contrib.specialagent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;

class ConstructorDigest extends Digest implements Comparable<ConstructorDigest> {
  private static final long serialVersionUID = -6005870987922050364L;

  static ConstructorDigest[] recurse(final Constructor<?>[] methods, final int index, final int depth) {
    for (int i = index; i < methods.length; ++i) {
      if (!methods[i].isSynthetic() && !Modifier.isPrivate(methods[i].getModifiers())) {
        final ConstructorDigest digest = new ConstructorDigest(methods[i]);
        final ConstructorDigest[] digests = recurse(methods, i + 1, depth + 1);
        digests[depth] = digest;
        return digests;
      }
    }

    return depth == 0 ? null : new ConstructorDigest[depth];
  }

  private final String[] parameterTypes;
  private final String[] exceptionTypes;

  ConstructorDigest(final Constructor<?> constructor) {
    this.parameterTypes = DigestUtil.getNames(constructor.getParameterTypes());
    this.exceptionTypes = DigestUtil.sort(DigestUtil.getNames(constructor.getExceptionTypes()));
  }

  public String[] getParameterTypes() {
    return this.parameterTypes;
  }

  public String[] getExceptionTypes() {
    return this.exceptionTypes;
  }

  @Override
  public int compareTo(final ConstructorDigest o) {
    final int comparison = Arrays.compare(parameterTypes, o.parameterTypes);
    if (comparison != 0)
      return comparison;

    return Arrays.compare(exceptionTypes, o.exceptionTypes);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof ConstructorDigest))
      return false;

    final ConstructorDigest that = (ConstructorDigest)obj;
    if (parameterTypes == null ? that.parameterTypes != null : that.parameterTypes == null || !Arrays.equals(parameterTypes, that.parameterTypes))
      return false;

    if (exceptionTypes == null ? that.exceptionTypes != null : that.exceptionTypes == null || !Arrays.equals(exceptionTypes, that.exceptionTypes))
      return false;

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("(");
    if (parameterTypes != null)
      builder.append(DigestUtil.toString(parameterTypes, ", "));

    builder.append(")");
    if (exceptionTypes != null)
      builder.append(" throws ").append(DigestUtil.toString(exceptionTypes, ", "));

    builder.append(";");
    return builder.toString();
  }
}