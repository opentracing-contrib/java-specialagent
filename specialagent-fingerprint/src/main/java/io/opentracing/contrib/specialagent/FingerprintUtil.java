/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class FingerprintUtil {
  private static final String[] includePrefixes = {"javax.jms."};
  private static final String[] excludePrefixes = {"io.opentracing.", "java.", "javax.", "net.bytebuddy.", "org.ietf.jgss", "org.jcp.xml.dsig.internal.", "org.jvnet.staxex.", "org.w3c.dom.", "org.xml.sax.", "sun."};

  static boolean isExcluded(final String className) {
    for (int i = 0; i < includePrefixes.length; ++i)
      if (className.startsWith(includePrefixes[i]))
        return false;

    for (int i = 0; i < excludePrefixes.length; ++i)
      if (className.startsWith(excludePrefixes[i]))
        return true;

    return false;
  }

  /**
   * Tests whether the {@code ACC_SYNTHETIC} bit is set in the specified access
   * modifier.
   *
   * @param mod The access modifier to test.
   * @return {@code true} if the {@code ACC_SYNTHETIC} bit is set in the
   *         specified access modifier.
   */
  static boolean isSynthetic(final int mod) {
    return (mod & Opcodes.ACC_SYNTHETIC) != 0;
  }

  static boolean isGetStatic(final int mod) {
    return (mod & Opcodes.GETSTATIC) != 0;
  }

  static boolean isPutStatic(final int mod) {
    return (mod & Opcodes.PUTSTATIC) != 0;
  }

  static boolean isInvokeStatic(final int mod) {
    return (mod & Opcodes.INVOKESTATIC) != 0;
  }

  public static boolean isInvokeSpecial(final int mod) {
    return (mod & Opcodes.INVOKESPECIAL) != 0;
  }

  public static boolean isPrimitive(final Type type) {
    if (type.getSort() == Type.ARRAY)
      throw new IllegalArgumentException("Type cannot be an array type");

    final String name = type.getClassName();
    return "boolean".equals(name) || "byte".equals(name) || "char".equals(name) || "short".equals(name) || "int".equals(name) || "long".equals(name) || "float".equals(name) || "double".equals(name);
  }

  private FingerprintUtil() {
  }
}