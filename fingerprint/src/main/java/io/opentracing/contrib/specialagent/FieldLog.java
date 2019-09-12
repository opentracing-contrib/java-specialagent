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

import java.util.Objects;

class FieldLog extends Log {
  final String fieldName;
  private String fieldType;

  FieldLog(final String className, final String fieldName) {
    super(className);
    this.fieldName = Objects.requireNonNull(fieldName);
  }

  String getFieldName() {
    return this.fieldName;
  }

  String getFieldType() {
    return this.fieldType;
  }

  void resolve(final String fieldType) {
    this.fieldType = Objects.requireNonNull(fieldType);
    resolve();
  }

  @Override
  public int hashCode() {
    return getClassName().hashCode() ^ fieldName.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;

    if (!(obj instanceof FieldLog))
      return false;

    final FieldLog that = (FieldLog)obj;
    return getClassName().equals(that.getClassName()) && fieldName.equals(that.fieldName);
  }

  @Override
  public String toString() {
    return super.toString() + "#" + fieldName + ":" + fieldType;
  }
}