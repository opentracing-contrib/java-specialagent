/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.dynamic;

public final class DynamicSpec {
  private static String[] empty = new String[0];

  private static IllegalArgumentException error(final String rule) {
    return new IllegalArgumentException("Malformed dynamic rule: " + rule);
  }

  private static void trim(final String[] array) {
    for (int i = 0; i < array.length; ++i)
      array[i] = array[i].trim();
  }

  public static DynamicSpec[] parseRules(final String rule) {
    final String[] rules = rule.trim().split(";");
    final DynamicSpec[] specs = new DynamicSpec[rules.length];
    for (int i = 0; i < rules.length; ++i) {
      final String[] classNameMethodSpec = rules[i].split("#");
      if (classNameMethodSpec.length != 2)
        throw error(rules[i]);

      final boolean polymorphic = classNameMethodSpec[0].startsWith("^");
      if (polymorphic)
        classNameMethodSpec[0] = classNameMethodSpec[0].substring(1).trim();

      trim(classNameMethodSpec);
      final String className = classNameMethodSpec[0];
      if (className.isEmpty())
        throw error(rules[i]);

      final String returning;
      final int col = classNameMethodSpec[1].indexOf(':');
      if (col == -1) {
        returning = null;
      }
      else {
        if (classNameMethodSpec[1].endsWith(":"))
          throw error(rules[i]);

        returning = classNameMethodSpec[1].substring(col + 1).trim();
        if (returning.isEmpty() || returning.contains(":"))
          throw error(rules[i]);

        classNameMethodSpec[1] = classNameMethodSpec[1].substring(0, col).trim();
      }

      final String methodName;
      final String[] args;
      final int start = classNameMethodSpec[1].indexOf('(');
      if (start == -1) {
        methodName = classNameMethodSpec[1];
        args = null;
      }
      else {
        final int end = classNameMethodSpec[1].indexOf(')', start + 1);
        if (end == -1)
          throw error(rules[i]);

        args = start + 1 == end ? empty : classNameMethodSpec[1].substring(start + 1, end).split(",");
        trim(args);

        methodName = classNameMethodSpec[1].substring(0, start).trim();
      }

      if (methodName.isEmpty() || ("<init>".equals(methodName) && returning != null) || ("<clinit>".equals(methodName) && (args != null || returning != null)))
        throw error(rules[i]);

      specs[i] = new DynamicSpec(polymorphic, className, methodName, args, returning);
    }

    return specs;
  }

  public final boolean polymorphic;
  public final String className;
  public final String methodName;
  public final String[] args;
  public final String returning;

  private DynamicSpec(final boolean polymorphic, final String className, final String methodName, final String[] args, final String returning) {
    this.polymorphic = polymorphic;
    this.className = className;
    this.methodName = methodName;
    this.args = args;
    this.returning = returning;
  }
}