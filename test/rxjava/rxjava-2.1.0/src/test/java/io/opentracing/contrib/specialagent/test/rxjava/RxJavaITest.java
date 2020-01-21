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

package io.opentracing.contrib.specialagent.test.rxjava;

import io.opentracing.contrib.specialagent.TestUtil;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class RxJavaITest {
  public static void main(final String[] args) throws Exception {
    Observable.range(1, 5).subscribe(new Observer<Integer>() {
      @Override
      public void onSubscribe(final Disposable d) {
        System.out.println("on subscribe: " + d);
      }

      @Override
      public void onNext(final Integer s) {
        System.out.println("on next " + s);
      }

      @Override
      public void onError(final Throwable e) {
        System.out.println("on error: " + e);
      }

      @Override
      public void onComplete() {
        System.out.println("on complete");
      }
    });

    Observable.just("Hello", "World").subscribe(System.out::println);
    TestUtil.checkSpan("rxjava-2", 2);
  }
}