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

package io.opentracing.contrib.specialagent.spring3.webmvc;

import org.springframework.beans.factory.access.BeanFactoryLocator;

/**
 * This interface is here purely to put "BeanFactoryLocator" on the fingerprint.
 * The presence of "BeanFactoryLocator" identifies a runtime as "Spring 3", as
 * opposed to "Spring 5".
 *
 * @author Seva Safris
 */
public interface Requires extends BeanFactoryLocator {
}