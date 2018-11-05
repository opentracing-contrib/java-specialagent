package io.opentracing.contrib.specialagent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

class MethodDigest extends NamedDigest<MethodDigest> {
  private static final long serialVersionUID = -6005870987922050364L;

  static MethodDigest[] recurse(final Method[] methods, final int index, final int depth) {
    for (int i = index; i < methods.length; ++i) {
      if (!methods[i].isSynthetic() && !Modifier.isPrivate(methods[i].getModifiers())) {
        final MethodDigest digest = new MethodDigest(methods[i]);
        final MethodDigest[] digests = recurse(methods, i + 1, depth + 1);
        digests[depth] = digest;
        return digests;
      }
    }

    return depth == 0 ? null : new MethodDigest[depth];
  }

  private final String returnType;
  private final String[] parameterTypes;
  private final String[] exceptionTypes;

  MethodDigest(final Method method) {
    super(method.getName());
    this.returnType = DigestUtil.getName(method.getReturnType());
    this.parameterTypes = DigestUtil.getNames(method.getParameterTypes());
    this.exceptionTypes = DigestUtil.sort(DigestUtil.getNames(method.getExceptionTypes()));
  }

  public String getReturnType() {
    return this.returnType;
  }

  public String[] getParameterTypes() {
    return this.parameterTypes;
  }

  public String[] getExceptionTypes() {
    return this.exceptionTypes;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof MethodDigest))
      return false;

    final MethodDigest that = (MethodDigest)obj;
    if (returnType != null ? !returnType.equals(that.returnType) : that.returnType != null)
      return false;

    if (parameterTypes == null ? that.parameterTypes != null : that.parameterTypes == null || !Arrays.equals(parameterTypes, that.parameterTypes))
      return false;

    if (exceptionTypes == null ? that.exceptionTypes != null : that.exceptionTypes == null || !Arrays.equals(exceptionTypes, that.exceptionTypes))
      return false;

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(returnType == null ? "void" : returnType).append(' ');
    builder.append(getName()).append("(");
    if (parameterTypes != null)
      builder.append(DigestUtil.toString(parameterTypes, ", "));

    builder.append(")");
    if (exceptionTypes != null)
      builder.append(" throws ").append(DigestUtil.toString(exceptionTypes, ", "));

    builder.append(";");
    return builder.toString();
  }
}