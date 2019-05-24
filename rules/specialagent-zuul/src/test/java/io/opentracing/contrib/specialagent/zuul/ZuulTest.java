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

package io.opentracing.contrib.specialagent.zuul;

import static junit.framework.TestCase.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.ZuulFilter;

import io.opentracing.contrib.specialagent.AgentRunner;

@RunWith(AgentRunner.class)
public class ZuulTest {
  @Test
  public void test() {
    final ZuulFilter preFilter = FilterLoader.getInstance().getFiltersByType("pre").get(0);
    assertTrue(preFilter instanceof TracePreZuulFilter);

    final ZuulFilter postFilter = FilterLoader.getInstance().getFiltersByType("post").get(0);
    assertTrue(postFilter instanceof TracePostZuulFilter);
  }
}