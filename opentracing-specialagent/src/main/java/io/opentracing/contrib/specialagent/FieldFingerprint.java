package io.opentracing.contrib.specialagent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class FieldFingerprint extends NamedFingerprint<FieldFingerprint> {
  private static final long serialVersionUID = 3516568839736210165L;

  static FieldFingerprint[] recurse(final Field[] fields, final int index, final int depth) {
    for (int i = index; i < fields.length; ++i) {
      if (!Modifier.isPrivate(fields[i].getModifiers())) {
        final FieldFingerprint digest = new FieldFingerprint(fields[i]);
        final FieldFingerprint[] digests = recurse(fields, i + 1, depth + 1);
        digests[depth] = digest;
        return digests;
      }
    }

    return depth == 0 ? null : new FieldFingerprint[depth];
  }

  private final String type;

  FieldFingerprint(final Field field) {
    super(field.getName());
    this.type = Util.getName(field.getType());
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof FieldFingerprint))
      return false;

    final FieldFingerprint that = (FieldFingerprint)obj;
    return type != null ? type.equals(that.type) : that.type == null;
  }

  @Override
  public String toString() {
    return type + " " + getName();
  }
}