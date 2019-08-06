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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public final class Logger {
  static final String LOG_LEVEL_PROPERTY = "sa.log.level";
  static final String LOG_FILE_PROPERTY = "sa.log.file";

  private static final Logger logger = new Logger();
  private static Level level = Level.INFO;
  private static PrintStream out = System.out;

  static {
    try {
      // Load user log level
      final String logLevelProperty = System.getProperty(LOG_LEVEL_PROPERTY);
      if (logLevelProperty != null)
        Logger.setLevel(Level.parse(logLevelProperty));

      // Load user log file
      final String logFileProperty = System.getProperty(LOG_FILE_PROPERTY);
      if (logFileProperty != null)
        Logger.setOut(new PrintStream(new FileOutputStream(logFileProperty), true));
    }
    catch (final FileNotFoundException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static Logger getLogger(final Class<?> cls) {
    return logger;
  }

  public static void setLevel(final Level level) {
    Logger.level = level != null ? level : Level.INFO;
  }

  public static void setOut(final PrintStream out) {
    Logger.out = out;
  }

  public boolean isLoggable(final Level level) {
    return Logger.level.isLoggable(level);
  }

  public void severe(final String msg) {
    if (isLoggable(Level.SEVERE))
      out.println(msg);
  }

  public void warning(final String msg) {
    if (isLoggable(Level.WARNING))
      out.println(msg);
  }

  public void info(final String msg) {
    if (isLoggable(Level.INFO))
      out.println(msg);
  }

  public void fine(final String msg) {
    if (isLoggable(Level.FINE))
      out.println(msg);
  }

  public void finer(final String msg) {
    if (isLoggable(Level.FINER))
      out.println(msg);
  }

  public void finest(final String msg) {
    if (isLoggable(Level.FINEST))
      out.println(msg);
  }

  public void log(final Level level, final String msg, final Throwable thrown) {
    if (isLoggable(level)) {
      out.println(msg);
      if (thrown != null)
        thrown.printStackTrace(out);
    }
  }

  public void log(final Level level, final String msg) {
    if (isLoggable(level))
      out.println(msg);
  }

  private Logger() {
  }
}