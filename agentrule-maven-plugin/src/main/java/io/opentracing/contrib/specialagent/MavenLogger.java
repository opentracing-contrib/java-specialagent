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

import org.apache.maven.plugin.logging.Log;

class MavenLogger extends Logger {
  private final Log log;

  MavenLogger(final Log log) {
    this.log = Objects.requireNonNull(log);
  }

  @Override
  public boolean isLoggable(final Level level) {
    if (level.ordinal() < Level.INFO.ordinal())
      return log.isDebugEnabled();

    if (level == Level.INFO)
      return log.isInfoEnabled();

    if (level == Level.WARNING)
      return log.isWarnEnabled();

    return log.isErrorEnabled();
  }

  @Override
  public void log(final Level level, final String msg, final Throwable thrown) {
    if (level.ordinal() < Level.INFO.ordinal())
      log.debug(msg, thrown);
    else if (level == Level.INFO)
      log.info(msg, thrown);
    else if (level == Level.WARNING)
      log.warn(msg, thrown);
    else
      log.error(msg, thrown);
  }

  @Override
  public void log(final Level level, final String msg) {
    log(level, msg, null);
  }

  @Override
  public void severe(final String msg) {
    log.error(msg);
  }

  @Override
  public void warning(final String msg) {
    log.warn(msg);
  }

  @Override
  public void info(final String msg) {
    log.info(msg);
  }

  @Override
  public void fine(final String msg) {
    log.debug(msg);
  }

  @Override
  public void finer(final String msg) {
    log.debug(msg);
  }

  @Override
  public void finest(final String msg) {
    log.debug(msg);
  }
}