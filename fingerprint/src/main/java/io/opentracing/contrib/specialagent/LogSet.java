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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class LogSet {
  private final HashMap<String,Map<Log,Log>> map = new LinkedHashMap<>(100);
  private final Logger logger;

  LogSet(final Logger logger) {
    this.logger = logger;
  }

  ClassLog addClassLog(final String className) {
    return add(new ClassLog(className));
  }

  FieldLog addFieldLog(final String className, final String name) {
    return add(new FieldLog(className, name));
  }

  MethodLog getMethodLog(final String className, final String methodName, final String returnType, final List<String> parameterTypes) {
    final Map<Log,Log> logs = map.get(className);
    return logs == null ? null : (MethodLog)logs.get(new MethodLog(className, methodName, returnType, parameterTypes));
  }

  MethodLog addMethodLog(final String className, final String methodName, final String returnType, final List<String> parameterTypes) {
    return add(new MethodLog(className, methodName, returnType, parameterTypes));
  }

  @SuppressWarnings("unchecked")
  private <T extends Log>T add(final T log) {
    Map<Log,Log> logs = map.get(log.getClassName());
    if (logs == null)
      map.put(log.getClassName(), logs = new LinkedHashMap<>());

    final T exists = (T)logs.get(log);
    final T add = exists == null || log.isResolved() ? log : exists;
    logs.put(add, add);
    return add;
  }

  Log get(final Log log) {
    final Map<Log,Log> logs = map.get(log.getClassName());
    return logs == null ? null : logs.get(log);
  }

  private final Set<String> checked = new HashSet<>();

  boolean compass(final Fingerprinter fingerprinter) throws IOException {
    final int before = map.keySet().size();
    int count = 0;
    for (final Map<Log,Log> logs : new ArrayList<>(map.values())) {
      for (final Log log : new ArrayList<>(logs.keySet())) {
        if (!log.isResolved()) {
          ++count;
          final String className = log.getClassName();
          if (checked.contains(className))
            continue;

          checked.add(className);
          if (!fingerprinter.fingerprint(className.replace('.', '/').concat(".class"))) {
            // Check if the fingerprinter was able to find the referenced class.
            // If not, it means it's been deliberately or conditionally excluded by
            // the agent rule or plugin dependency spec.
            map.remove(log.getClassName());
            logger.warning("Skipping fingerprint for missing class: " + log.getClassName());
            break;
          }
        }
      }
    }

    logger.info("Fingerprinted: " + checked.size() + ", Remaining: " + count);
    return before != map.keySet().size();
  }

  boolean contains(final Log log) {
    final Map<Log,Log> logs = map.get(log.getClassName());
    return logs != null && logs.containsKey(log);
  }

  void markAllResolved(final String className, final boolean resolveConstructors) {
    final Iterator<Map.Entry<String,Map<Log,Log>>> mapIterator = map.entrySet().iterator();
    while (mapIterator.hasNext()) {
      final Map<Log,Log> logs = mapIterator.next().getValue();
      final Iterator<Map.Entry<Log,Log>> logsIterator = logs.entrySet().iterator();
      while (logsIterator.hasNext()) {
        final Log log = logsIterator.next().getKey();
        if (className.equals(log.getClassName()) && !log.isResolved()) {
          if (resolveConstructors && log instanceof MethodLog && "<init>".equals(((MethodLog)log).getMethodName())) {
            log.resolve();
          }
          else {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("Unresolved: " + log);

            logsIterator.remove();
          }
        }
      }

      if (logs.size() == 0)
        mapIterator.remove();
    }
  }

  private static final Comparator<Log> comparator = new Comparator<Log>() {
    @Override
    public int compare(final Log o1, final Log o2) {
      final int comparison = o1.getClassName().compareTo(o2.getClassName());
      if (comparison != 0)
        return comparison;

      final int c1 = o1 instanceof ClassLog ? 0 : o1 instanceof MethodLog ? 1 : 2;
      final int c2 = o2 instanceof ClassLog ? 0 : o2 instanceof MethodLog ? 1 : 2;
      return Integer.compare(c1, c2);
    }
  };

  private List<List<Log>> getSortedLogs() {
    final Map<String,List<Log>> sortedMap = new HashMap<>();
    for (final Map.Entry<String,Map<Log,Log>> entry : map.entrySet()) {
      final List<Log> sorted = new ArrayList<>(entry.getValue().keySet());

      // Remove unresolved logs
      final Iterator<Log> iterator = sorted.iterator();
      while (iterator.hasNext())
        if (!iterator.next().isResolved())
          iterator.remove();

      if (sorted.size() == 0)
        continue;

      Collections.sort(sorted, comparator);
      sortedMap.put(entry.getKey(), sorted);
    }

    final Digraph<String> digraph = new Digraph<>();
    for (final List<Log> logs : sortedMap.values()) {
      final ClassLog classLog = (ClassLog)logs.get(0);
      if (classLog.getSuperClass() != null)
        digraph.add(classLog.getClassName(), classLog.getSuperClass());
      else
        digraph.add(classLog.getClassName());
    }

    final List<List<Log>> sortedList = new ArrayList<>();
    for (final String className : digraph.topSort()) {
      final List<Log> logs = sortedMap.get(className);
      if (logs != null)
        sortedList.add(logs);
    }

    return sortedList;
  }

  List<ClassFingerprint> collate() {
    final List<List<Log>> sorted = getSortedLogs();

    final Map<String,ClassFingerprint> classNameToFingerprint = new HashMap<>();
    final List<ClassFingerprint> classes = new ArrayList<>();
    for (final List<Log> logs : sorted) {
      String className = null;
      String superClass = null;
      LinkedHashSet<FieldFingerprint> fields = null;
      LinkedHashSet<MethodFingerprint> methods = null;
      LinkedHashSet<ConstructorFingerprint> constructors = null;
      for (int i = 0; i < logs.size(); ++i) {
        final Log log = logs.get(i);
        if (i == 0) {
          if (!(log instanceof ClassLog) || log.getClassName().equals(className))
            throw new IllegalStateException("\n" + AssembleUtil.toIndentedString(sorted));

          className = log.getClassName();
          superClass = ((ClassLog)log).getSuperClass();
          fields = new LinkedHashSet<>();
          methods = new LinkedHashSet<>();
          constructors = new LinkedHashSet<>();
        }
        else if (log instanceof FieldLog) {
          final FieldLog fieldLog = (FieldLog)log;
          fields.add(new FieldFingerprint(fieldLog.getFieldName(), fieldLog.getFieldType()));
        }
        else if (log instanceof MethodLog) {
          final MethodLog methodLog = (MethodLog)log;
          if ("<init>".equals(methodLog.getMethodName()))
            constructors.add(new ConstructorFingerprint(methodLog.getParameterTypes() == null ? null : methodLog.getParameterTypes(), methodLog.getExceptionTypes() == null ? null : methodLog.getExceptionTypes()));
          else
            methods.add(new MethodFingerprint(methodLog.getMethodName(), "void".equals(methodLog.getReturnType()) ? null : methodLog.getReturnType(), methodLog.getParameterTypes() == null ? null : methodLog.getParameterTypes(), methodLog.getExceptionTypes() == null ? null : methodLog.getExceptionTypes()));
        }
        else {
          throw new UnsupportedOperationException("Unsupported type: " + log.getClass().getName());
        }
      }

      if (superClass != null)
        include(superClass, classNameToFingerprint, constructors, methods, fields);

      final ClassFingerprint fingerprint = new ClassFingerprint(className, superClass, new ArrayList<>(constructors), new ArrayList<>(methods), new ArrayList<>(fields));
      classNameToFingerprint.put(className, fingerprint);
      classes.add(fingerprint);
    }

    return classes;
  }

  private void include(final String className, final Map<String,ClassFingerprint> classNameToFingerprint, final Collection<ConstructorFingerprint> constructors, final Collection<MethodFingerprint> methods, final Collection<FieldFingerprint> fields) {
    final ClassFingerprint classFingerprint = classNameToFingerprint.get(className);
    if (classFingerprint == null)
      return;

    if (classFingerprint.getConstructors() != null)
      constructors.addAll(classFingerprint.getConstructors());

    if (classFingerprint.getMethods() != null)
      methods.addAll(classFingerprint.getMethods());

    if (classFingerprint.getFields() != null)
      fields.addAll(classFingerprint.getFields());

    if (classFingerprint.getSuperClass() != null)
      include(classFingerprint.getSuperClass(), classNameToFingerprint, constructors, methods, fields);
  }

  @Override
  public String toString() {
    if (map.values().size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    for (final Map<Log,Log> logs : map.values())
      for (final Log log : logs.keySet())
        builder.append(log).append('\n');

    builder.setLength(builder.length() - 1);
    return builder.toString();
  }

  public void purge(final Set<String> excludeClassNames) {
    final Iterator<String> iterator = map.keySet().iterator();
    while (iterator.hasNext())
      if (excludeClassNames.contains(iterator.next()))
        iterator.remove();
  }
}