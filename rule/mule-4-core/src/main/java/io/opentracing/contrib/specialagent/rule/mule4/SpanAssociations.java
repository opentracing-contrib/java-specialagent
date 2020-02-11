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

package io.opentracing.contrib.specialagent.rule.mule4;

import io.opentracing.Span;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class SpanAssociations {

    private static final SpanAssociations INSTANCE = new SpanAssociations();
    private static final Map<Object, Span> spanAssociations = Collections.synchronizedMap(new WeakHashMap<Object, Span>());

    private SpanAssociations() {
    }

    public static SpanAssociations get() {
        return INSTANCE;
    }

    /**
     * This method establishes an association between an application object
     * (i.e. the subject of the instrumentation) and a span. Once the
     * application object is no longer being used, the association with the
     * span will automatically be discarded.
     *
     * @param obj The application object to be associated with the span
     * @param span The span
     */
    public void associateSpan(Object obj, Span span) {
        spanAssociations.putIfAbsent(obj, span);
    }

    /**
     * This method retrieves the span associated with the supplied application
     * object.
     *
     * @param obj The application object
     * @return The span, or null if no associated span exists
     */
    public Span retrieveSpan(Object obj) {
        return spanAssociations.get(obj);
    }

}
