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

package org.codehaus.mojo.exec;

import java.lang.Thread.UncaughtExceptionHandler;

public class Exec implements UncaughtExceptionHandler {
  public static void main(final String[] args) throws Exception {
    Thread.currentThread().setUncaughtExceptionHandler(new Exec());
    final String execClass = args[0];
    final String[] execArgs = new String[args.length - 1];
    System.arraycopy(args, 1, execArgs, 0, execArgs.length);
    Class.forName(execClass).getMethod("main", String[].class).invoke(null, (Object)execArgs);
  }

  @Override
  public void uncaughtException(final Thread t, final Throwable e) {
    e.printStackTrace(System.err);
    System.exit(1);
  }
}