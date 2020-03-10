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
import java.util.Iterator;
import java.util.Map;

public class Logger {
  private static String REFRESH_PREFIX = "sa.log.refresh.";
  private static final String LOG_REFRESH_PROPERTY = REFRESH_PREFIX + Logger.class.hashCode();
  static final String LOG_LEVEL_PROPERTY = "sa.log.level";
  static final String LOG_FILE_PROPERTY = "sa.log.file";

  private static final Logger logger = new Logger();
  private static Level level = Level.INFO;
  private static PrintStream out = System.err;

  static {
    init();
  }

  private static void recurseClearProperty(final Iterator<Map.Entry<Object,Object>> iterator) {
    while (iterator.hasNext()) {
      final Map.Entry<Object,Object> entry = iterator.next();
      final String key = String.valueOf(entry.getKey());
      if (key.startsWith(REFRESH_PREFIX)) {
        recurseClearProperty(iterator);
        System.clearProperty(key);
      }
    }
  }

  static void refreshLoggers() {
    recurseClearProperty(System.getProperties().entrySet().iterator());
  }

  static void init() {
    // Load user log level
    final String logLevelProperty = System.getProperty(LOG_LEVEL_PROPERTY);
    if (logLevelProperty != null)
      Logger.setLevel(Level.parse(logLevelProperty));

    // Load user log file
    final String logFileProperty = System.getProperty(LOG_FILE_PROPERTY);
    if (logFileProperty != null) {
      try {
        Logger.setOut(new PrintStream(new FileOutputStream(logFileProperty), true));
      }
      catch (final FileNotFoundException e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }

  private static void refresh() {
    if (System.getProperty(LOG_REFRESH_PROPERTY) == null) {
      System.setProperty(LOG_REFRESH_PROPERTY, "");
      init();
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
    refresh();
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

  protected Logger() {
  }
}