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

package io.opentracing.mock;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import io.opentracing.SpanContext;
import io.opentracing.mock.MockSpan.MockContext;

class ProxyMockSpanContext extends MockContext {
  final SpanContext mockSpanContext;
  final SpanContext realSpanContext;

  ProxyMockSpanContext(final MockContext mockSpanContext, final SpanContext realSpanContext) {
    super(-1, -1, null);
    this.mockSpanContext = Objects.requireNonNull(mockSpanContext);
    this.realSpanContext = Objects.requireNonNull(realSpanContext);
  }

  @Override
  public Iterable<Entry<String,String>> baggageItems() {
    final Iterable<Entry<String,String>> mockBaggageItems = mockSpanContext.baggageItems();
    final Iterable<Entry<String,String>> realBaggageItems = realSpanContext.baggageItems();
    if (mockBaggageItems != null ? realBaggageItems == null : realBaggageItems != null)
      throw new IllegalStateException();

    return mockBaggageItems == null ? null : new ProxyIterator(mockBaggageItems.iterator(), realBaggageItems.iterator());
  }

  private class ProxyIterator implements Iterable<Entry<String,String>>, Iterator<Entry<String,String>> {
    private final Iterator<Entry<String,String>> mockIterator;
    private final Iterator<Entry<String,String>> realIterator;

    private ProxyIterator(final Iterator<Entry<String,String>> mockIterator, final Iterator<Entry<String,String>> realIterator) {
      this.mockIterator = Objects.requireNonNull(mockIterator);
      this.realIterator = Objects.requireNonNull(realIterator);
    }

    @Override
    public Iterator<Entry<String,String>> iterator() {
      return this;
    }

    @Override
    public boolean hasNext() {
      final boolean mockHasNext = mockIterator.hasNext();
      final boolean realHasNext = realIterator.hasNext();
      if (mockHasNext != realHasNext)
        throw new IllegalStateException();

      return mockHasNext;
    }

    private class ProxyEntry implements Entry<String,String> {
      private final Entry<String,String> mockEntry;
      private final Entry<String,String> realEntry;

      private ProxyEntry(final Entry<String,String> mockEntry, final Entry<String,String> realEntry) {
        if (mockEntry != null ? realEntry == null || !mockEntry.getKey().equals(realEntry.getKey()) || !mockEntry.getValue().equals(realEntry.getValue()) : realEntry != null)
          throw new IllegalStateException();

        this.mockEntry = mockEntry;
        this.realEntry = realEntry;
      }

      @Override
      public String getKey() {
        return mockEntry.getKey();
      }

      @Override
      public String getValue() {
        return mockEntry.getValue();
      }

      @Override
      public String setValue(final String value) {
        final String mockValue = mockEntry.setValue(value);
        final String realValue = realEntry.setValue(value);
        if (mockValue != null ? !mockValue.equals(realValue) : realValue != null)
          throw new IllegalStateException();

        return mockValue;
      }
    }

    @Override
    public Entry<String,String> next() {
      final Entry<String,String> mockNext = mockIterator.next();
      final Entry<String,String> realNext = realIterator.next();
      return new ProxyEntry(mockNext, realNext);
    }

    @Override
    public void remove() {
      mockIterator.remove();
      realIterator.remove();
    }
  }
}