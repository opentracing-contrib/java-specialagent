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

package io.opentracing.contrib.specialagent.rule.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;

public class MockSpanDecorator implements ServletFilterSpanDecorator {
	
	public static String MOCK_TAG_KEY = "mock-tag-key";
	public static String MOCK_TAG_VALUE = "mock-tag-value";

	@Override
	public void onRequest(HttpServletRequest httpServletRequest, Span span) {
		span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
	}

	@Override
	public void onResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Span span) {
		span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
	}

	@Override
	public void onError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
			Throwable exception, Span span) {
		span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
	}

	@Override
	public void onTimeout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, long timeout,
			Span span) {
		span.setTag(MOCK_TAG_KEY, MOCK_TAG_VALUE);
	}
}
