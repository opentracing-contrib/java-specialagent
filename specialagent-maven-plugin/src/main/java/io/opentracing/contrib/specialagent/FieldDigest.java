package io.opentracing.contrib.specialagent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class FieldDigest extends NamedDigest<FieldDigest> {
  private static final long serialVersionUID = 3516568839736210165L;

  static FieldDigest[] recurse(final Field[] fields, final int index, final int depth) {
    for (int i = index; i < fields.length; ++i) {
      if (!Modifier.isPrivate(fields[i].getModifiers())) {
        final FieldDigest digest = new FieldDigest(fields[i]);
        final FieldDigest[] digests = recurse(fields, i + 1, depth + 1);
        digests[depth] = digest;
        return digests;
      }
    }

    return depth == 0 ? null : new FieldDigest[depth];
  }

  private final String type;

  FieldDigest(final Field field) {
    super(field.getName());
    this.type = DigestUtil.getName(field.getType());
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof FieldDigest))
      return false;

    final FieldDigest that = (FieldDigest)obj;
    return type != null ? type.equals(that.type) : that.type == null;
  }

  @Override
  public String toString() {
    return type + " " + getName();
  }
}