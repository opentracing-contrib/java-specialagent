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

package io.opentracing.contrib.specialagent.test.jdbi;


import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import org.h2.Driver;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

public class Jdbi3ITest {
  public static void main(String[] args) {
    Driver.load();
    final Jdbi dbi = Jdbi.create("jdbc:h2:mem:dbi", "sa", "");
    final Handle handle = dbi.open();
    handle.execute("CREATE TABLE employer (id INTEGER)");
    handle.close();

    TestUtil.checkSpan(new ComponentSpanCount("java-jdbc", 2));
  }
}
