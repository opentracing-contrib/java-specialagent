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

package io.opentracing.contrib.specialagent.spring.messaging;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import io.opentracing.contrib.specialagent.AgentRunner;

@Ignore
@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class TypeExcludeFilterTest {
  public static class TestTypeExcludeFilter extends TypeExcludeFilter {
    @Override
    public boolean match(final MetadataReader metadataReader, final MetadataReaderFactory metadataReaderFactory) throws IOException {
      throw new NoClassDefFoundError();
    }
  }

  public static class TestObjectProvider<T> implements ObjectProvider<T> {
    @Override
    public T getIfAvailable() throws BeansException {
      throw new NoClassDefFoundError();
    }

    @Override
    public T getObject() throws BeansException {
      return null;
    }

    @Override
    public T getObject(final Object ... args) throws BeansException {
      return null;
    }

    @Override
    public T getIfUnique() throws BeansException {
      return null;
    }
  }

  @Test
  public void testTypeExcludeFilter() throws IOException {
    final TypeExcludeFilter test = new TestTypeExcludeFilter();
    assertFalse(test.match(null, null));
  }

  @Test
  public void testObjectProvider() {
    final ObjectProvider<?> test = new TestObjectProvider<>();
    assertNull(test.getIfAvailable());
  }
}