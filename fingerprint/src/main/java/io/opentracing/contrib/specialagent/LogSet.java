/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, final Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class LogSet {
  private final LinkedHashMap<Log,Log> map = new LinkedHashMap<>();

  private final boolean debug;

  LogSet(final boolean debug) {
    this.debug = debug;
  }

  @SuppressWarnings("unchecked")
  <T extends Log>T add(final T log) {
    final T exists = (T)map.get(log);
    final Phase phase = exists == null || log.getPhase().ordinal() < exists.getPhase().ordinal() ? log.getPhase() : exists.getPhase();
    final T add = exists == null || log.isResolved() ? log : exists;
    add.setPhase(phase);
    map.put(add, add);
    return add;
  }

  Log get(final Log log) {
    return map.get(log);
  }

  void addAll(final LogSet scan) {
    for (final Log log : scan.map.values())
      add(log);
  }

  Log nextLog() {
    for (final Map.Entry<Log,Log> entry : map.entrySet())
      if (!entry.getValue().isResolved())
        return entry.getValue();

    return null;
  }

  public boolean contains(final Log log) {
    return map.containsKey(log);
  }

  public void markAllResolved(final String className) {
    final Iterator<Map.Entry<Log,Log>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      final Log log = iterator.next().getValue();
      if (className.equals(log.getClassName()) && !log.isResolved()) {
        if (log instanceof MethodLog && ((MethodLog)log).methodName.equals("<init>")) {
          log.resolve();
        }
        else {
          iterator.remove();
          if (debug)
            System.err.println("Removed: " + log);
        }
      }
    }
  }

  public ClassFingerprint[] collate(final Phase phase) {
    final List<Log> sorted = new ArrayList<>(map.values());
    Collections.sort(sorted, new Comparator<Log>() {
      @Override
      public int compare(final Log o1, final Log o2) {
        final int comparison = o1.className.compareTo(o2.className);
        if (comparison != 0)
          return comparison;

        final int c1 = o1 instanceof ClassLog ? 0 : o1 instanceof MethodLog ? 1 : 2;
        final int c2 = o2 instanceof ClassLog ? 0 : o2 instanceof MethodLog ? 1 : 2;
        return Integer.compare(c1, c2);
      }
    });

    final List<ClassFingerprint> classes = new ArrayList<>();
    String className = null;
    String superClass = null;
    String[] interfaces = null;
    List<FieldFingerprint> fields = null;
    List<MethodFingerprint> methods = null;
    List<ConstructorFingerprint> constructors = null;
    for (final Log log : sorted) {
      if (log.getPhase().ordinal() > phase.ordinal())
        continue;

      if (!log.className.equals(className)) {
        if (!(log instanceof ClassLog))
          throw new IllegalStateException("\n" + AssembleUtil.toIndentedString(sorted));

        if (className != null)
          classes.add(new ClassFingerprint(className, superClass, interfaces, constructors, methods, fields));

        className = log.className;
        final ClassLog classLog = (ClassLog)log;
        superClass = classLog.getSuperClass();
        interfaces = classLog.getInterfaces();
        fields = new ArrayList<>();
        methods = new ArrayList<>();
        constructors = new ArrayList<>();
      }
      else {
        if (log instanceof FieldLog) {
          final FieldLog fieldLog = (FieldLog)log;
          fields.add(new FieldFingerprint(fieldLog.getFieldName(), fieldLog.getFieldType()));
        }
        else if (log instanceof MethodLog) {
          final MethodLog methodLog = (MethodLog)log;
          if ("<init>".equals(methodLog.methodName))
            constructors.add(new ConstructorFingerprint(methodLog.parameterTypes == null ? null : methodLog.parameterTypes.toArray(new String[methodLog.parameterTypes.size()]), methodLog.getExceptionTypes() == null ? null : methodLog.getExceptionTypes().toArray(new String[methodLog.getExceptionTypes().size()])));
          else
            methods.add(new MethodFingerprint(methodLog.methodName, methodLog.returnType, methodLog.parameterTypes == null ? null : methodLog.parameterTypes.toArray(new String[methodLog.parameterTypes.size()]), methodLog.getExceptionTypes() == null ? null : methodLog.getExceptionTypes().toArray(new String[methodLog.getExceptionTypes().size()])));
        }
        else {
          throw new UnsupportedOperationException("Unsupported type: " + log.getClass().getName());
        }
      }
    }

    if (className != null)
      classes.add(new ClassFingerprint(className, superClass, interfaces, constructors, methods, fields));

    return classes.toArray(new ClassFingerprint[classes.size()]);
  }

  public String toString(final Phase phase) {
    final StringBuilder builder = new StringBuilder();
    for (final Log log : map.values()) {
      if (phase == null || log.getPhase().ordinal() <= phase.ordinal())
        builder.append(log).append('\n');
    }

    builder.setLength(builder.length() - 1);
    return builder.toString();
  }

  @Override
  public String toString() {
    return toString(null);
  }
}