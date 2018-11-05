package io.opentracing.contrib.specialagent;

import java.util.Arrays;

class ClassDigest extends NamedDigest<ClassDigest> {
  private static final long serialVersionUID = 3179458505281585557L;

  private final String superClass;
  private final ConstructorDigest[] constructors;
  private final MethodDigest[] methods;
  private final FieldDigest[] fields;

  ClassDigest(final Class<?> cls) {
    super(cls.getName());
    this.superClass = cls.getSuperclass() == null || cls.getSuperclass() == Object.class ? null : cls.getSuperclass().getName();
    this.constructors = DigestUtil.sort(ConstructorDigest.recurse(cls.getDeclaredConstructors(), 0, 0));
    this.methods = DigestUtil.sort(MethodDigest.recurse(cls.getDeclaredMethods(), 0, 0));
    this.fields = DigestUtil.sort(FieldDigest.recurse(cls.getDeclaredFields(), 0, 0));
  }

  public String getSuperClass() {
    return this.superClass;
  }

  public FieldDigest[] getFields() {
    return this.fields;
  }

  public ConstructorDigest[] getConstructors() {
    return this.constructors;
  }

  public MethodDigest[] getMethods() {
    return this.methods;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof ClassDigest))
      return false;

    final ClassDigest that = (ClassDigest)obj;
    if (superClass != null ? !superClass.equals(that.superClass) : that.superClass != null)
      return false;

    if (constructors == null ? that.constructors != null : that.constructors == null || !Arrays.equals(constructors, that.constructors))
      return false;

    if (methods == null ? that.methods != null : that.methods == null || !Arrays.equals(methods, that.methods))
      return false;

    if (fields == null ? that.fields != null : that.fields == null || !Arrays.equals(fields, that.fields))
      return false;

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("class ").append(getName());
    if (superClass != null)
      builder.append(" extends ").append(superClass);

    builder.append(" {\n");
    if (constructors != null)
      builder.append("  ").append(getName()).append(DigestUtil.toString(constructors, "\n  " + getName())).append('\n');

    if (methods != null)
      builder.append("  ").append(DigestUtil.toString(methods, "\n  ")).append('\n');

    if (fields != null)
      builder.append("  ").append(DigestUtil.toString(fields, "\n  ")).append('\n');

    builder.append('}');
    return builder.toString();
  }
}