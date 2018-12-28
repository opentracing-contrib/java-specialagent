/* Copyright 2018 The OpenTracing Authors
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.byteman.agent.Main;
import org.jboss.byteman.agent.Retransformer;
import org.jboss.byteman.rule.Rule;

public class BytemanTransformer extends Transformer {
  private static final Logger logger = Logger.getLogger(BytemanTransformer.class.getName());
  private static Retransformer retransformer;

  BytemanTransformer() {
    super("otarules.btm");
  }

  @Override
  void premain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    Main.premain(addManager(agentArgs), instrumentation);
  }

  /**
   * Initializes the manager.
   *
   * @param retransformer The ByteMan retransformer.
   */
  public static void initialize(final Retransformer retransformer) {
    BytemanTransformer.retransformer = retransformer;
    Agent.initialize();
  }

  protected static String addManager(String agentArgs) {
    if (agentArgs == null || agentArgs.trim().isEmpty())
      agentArgs = "";
    else
      agentArgs += ",";

    agentArgs += "manager:" + BytemanTransformer.class.getName();
    return agentArgs;
  }

  /**
   * This method loads any OpenTracing Agent rules (otarules.btm) found as
   * resources within the supplied classloader.
   */
  @Override
  void loadRules(final ClassLoader allPluginsClassLoader, final Map<String,Integer> pluginJarToIndex, final String arg) throws IOException {
    final List<String> scripts = new ArrayList<>();
    final List<String> scriptNames = new ArrayList<>();

    // Prepare the ClassLoader rule
    digestRule(ClassLoader.getSystemClassLoader().getResource("classloader.btm"), null, scripts, scriptNames);

    // Prepare the Plugin rules
    final Enumeration<URL> enumeration = allPluginsClassLoader.getResources(file);
    while (enumeration.hasMoreElements()) {
      final URL scriptUrl = enumeration.nextElement();
      final String pluginJar = Util.getSourceLocation(scriptUrl, file);
      if (logger.isLoggable(Level.FINEST))
        logger.finest("Dereferencing index for " + pluginJar);

      final int index = pluginJarToIndex.get(pluginJar);
      digestRule(scriptUrl, index, scripts, scriptNames);
    }

    loadScripts(scripts, scriptNames, retransformer);
  }

  @Override
  boolean disableTriggers() {
    return Rule.disableTriggers();
  }

  @Override
  boolean enableTriggers() {
    return Rule.enableTriggers();
  }

  /**
   * Loads the specified scripts with script names into Byteman.
   *
   * @param scripts The list of scripts.
   * @param scriptNames The list of script names.
   */
  private static void loadScripts(final List<String> scripts, final List<String> scriptNames, final Retransformer retransformer) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Installing rules:\n" + Util.toIndentedString(scriptNames));

    if (logger.isLoggable(Level.FINEST))
      for (final String script : scripts)
        logger.finest(script);

    final StringWriter sw = new StringWriter();
    try (final PrintWriter pw = new PrintWriter(sw)) {
      retransformer.installScript(scripts, scriptNames, pw);
    }
    catch (final Exception e) {
      logger.log(Level.SEVERE, "Failed to install scripts", e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine(sw.toString());
  }

  /**
   * Unloads a rule from Byteman by the specified rule name.
   *
   * @param ruleName The name of the rule to unload.
   */
  private void unloadRule(final String ruleName, final Retransformer retransformer) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Unloading rule: " + ruleName);

    final StringWriter sw = new StringWriter();
    try (final PrintWriter pw = new PrintWriter(sw)) {
      retransformer.removeScripts(Collections.singletonList("RULE " + ruleName), pw);
    }
    catch (final Exception e) {
      logger.log(Level.SEVERE, "Failed to unload rule: " + ruleName, e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine(sw.toString());
  }


  /**
   * This method digests the Byteman rule script at {@code url}, and adds the
   * script to the {@code scripts} and {@code scriptNames} lists. If
   * {@code index} is not null, this method calls
   * {@link #retrofitScript(String,int)} to create a "Load Classes" script that
   * is triggered in the same manner as the script at {@code url}, and is used
   * to load API and instrumentation classes into the calling object's
   * {@code ClassLoader}.
   *
   * @param url The {@code URL} to the script resource.
   * @param index The index of the OpenTracing instrumentation JAR in
   *          {@code allPluginsClassLoader.getURLs()} which corresponds to
   *          {@code script}.
   * @param scripts The list into which the script will be added.
   * @param scriptNames The list into which the script name will be added.
   */
  private static void digestRule(final URL url, final Integer index, final List<String> scripts, final List<String> scriptNames) {
    if (logger.isLoggable(Level.FINE))
      logger.fine("Digest rule for index " + index + " from URL = " + url);

    final String script = Util.readBytes(url);
    scripts.add(index == null ? script : retrofitScript(script, index));
    scriptNames.add(url.toString());
  }

  /**
   * Writes a "Load Classes" rule into the specified {@code StringBuilder} for
   * the provided rule {@code header}, JAR reference {@code index}, and
   * {@code classRef}.
   *
   * @param builder The {@code StringBuilder} into which the script is to be
   *          written.
   * @param header The header of the rule for which the "Load Classes" rule is
   *          to be created.
   * @param index The index of the OpenTracing instrumentation JAR in
   *          {@code allPluginsClassLoader.getURLs()} which corresponds to
   *          {@code script}.
   * @param classRef A string representing the reference to the {@code Class}
   *          object on which the rule is to be triggered, or {@code null} if
   *          the class is an interface.
   */
  private static void writeLoadClassesRule(final StringBuilder builder, final String header, final int index, final String classRef) {
    final int start = header.indexOf("RULE ");
    final int end = header.indexOf('\n', start + 5);
    if (builder.length() > 0)
      builder.append('\n');

    builder.append(header.substring(0, end)).append(" (Load Classes)");
    builder.append(header.substring(end));
    builder.append("IF TRUE\n");
    builder.append("DO\n");
    builder.append("  traceln(\">>>>>>>> Load Classes " + index + "\");\n");
    builder.append("  ").append(Agent.class.getName()).append(".linkPlugin(").append(index).append(", ").append(classRef).append(", $*);\n");
    builder.append("ENDRULE\n");
  }

  /**
   * Tests if the specified {@code string} is present at the {@code index} in
   * the provided {@code script}. Each character that is read is appended to the
   * provided {@code StringBuilder}. This method tests each character in the
   * specified {@code script} for equality starting at {@code index} of the
   * provided {@code script}. If all characters in {@code string} match, this
   * method returns {@code index + string.length()}. If a character mismatch is
   * encountered at {@code string} index={@code i}, this method returns
   * {@code -i}.
   *
   * @param script The script in which {@code string} is to be matched.
   * @param string The string to match.
   * @param index The starting index in {@code script} from which to attempt to
   *          match {@code string}.
   * @param builder The {@code StringBuilder} into which each checked byte is to
   *          be appended.
   * @return If all characters in {@code string} match, this method returns
   *         {@code index + string.length()}. If a character mismatch is
   *         encountered at {@code string} index={@code i}, this method returns
   *         {@code -i}.
   */
  private static int match(final String script, final String string, int index, final StringBuilder builder) {
    if (index < 0)
      index = -index;

    int i = -1;
    for (int j; ++i < string.length() && (j = index + i) < script.length();) {
      final char ch = script.charAt(j);
      if (ch != string.charAt(i))
        return -index - i;

      builder.append(ch);
    }

    return index + i;
  }

  /**
   * This method consumes a Byteman script that is intended for the
   * instrumentation of the OpenTracing API into a 3rd-party library, and
   * produces a Byteman script that is used to trigger the "load classes"
   * procedure {@link #linkPlugin(int,Class,Object[])} that loads the
   * instrumentation and OpenTracing API classes directly into the
   * {@code ClassLoader} in which the 3rd-party library is loaded.
   *
   * @param script The OpenTracing instrumentation script.
   * @param index The index of the OpenTracing instrumentation JAR in
   *          {@code allPluginsClassLoader.getURLs()} which corresponds to
   *          {@code script}.
   * @return The script used to trigger the "Load Classes" procedure
   *         {@link #linkPlugin(int,Class,Object[])}.
   */
  static String retrofitScript(final String script, final int index) {
    final StringBuilder out = new StringBuilder();
    final StringBuilder builder = new StringBuilder();
    int ruleStart = 0;

    String var = null;
    boolean inBind = false;
    boolean hasBind = false;
    boolean preAdded = false;
    String classRef = null;
    String bindClause = null;
    for (int i = 0; i < script.length();) {
      final char ch = script.charAt(i);
      builder.append(ch);
      if (ch == ';') {
        var = null;
      }
      else if (ch == '\n') {
        int m = i + 1;
        m = match(script, "RULE ", m, builder);
        if (m > -1) {
          ruleStart = builder.length() - 5;
          if ((i = script.indexOf('\n', m)) == -1)
            i = script.length();

          builder.append(script.substring(m, i));
          continue;
        }

        m = match(script, "CLASS ", m, builder);
        if (m > -1) {
          if ((i = script.indexOf('\n', m)) == -1)
            i = script.length();

          classRef = script.substring(m, i).trim() + ".class";
          if (classRef.charAt(0) == '^')
            classRef = classRef.substring(1);

          builder.append(script.substring(m, i));
          continue;
        }

        m = match(script, "AT ", m, builder);
        if (m > -1) {
          inBind = false;
          i = m;
          continue;
        }

        m = match(script, "METHOD ", m, builder);
        if (m > -1) {
          inBind = false;
          i = m;
          bindClause = "\n  cOmPaTiBlE:boolean = " + Agent.class.getName() + ".linkPlugin(" + index + ", " + classRef + ", $*);";
          continue;
        }

        m = match(script, "ENDRULE", m, builder);
        if (m > -1) {
          inBind = false;
          hasBind = false;
          i = m;
          out.append(builder);
          builder.setLength(0);
          preAdded = false;
          continue;
        }

        m = match(script, "IF ", m, builder);
        if (m > -1) {
          if (!preAdded) {
            writeLoadClassesRule(out, builder.substring(ruleStart, builder.length() - 3), index, classRef);
            ruleStart = 0;
            preAdded = true;
          }

          if (!hasBind)
            builder.insert(builder.length() - 3, "BIND" + bindClause + "\n");

          inBind = false;
          if ((i = script.indexOf("\nDO", m)) == -1)
            i = script.length();

          final String condition = script.substring(m, i).trim();
          builder.append("cOmPaTiBlE");
          if (!"TRUE".equalsIgnoreCase(condition))
            builder.append(" AND (").append(condition).append(")");

          continue;
        }

        m = match(script, "BIND", m, builder);
        if (m > -1) {
          if (!preAdded) {
            writeLoadClassesRule(out, builder.substring(ruleStart, builder.length() - 4), index, classRef);
            ruleStart = 0;
            preAdded = true;
          }

          inBind = true;
          hasBind = true;
          builder.append(bindClause);
          i = m - 1;
        }

        if (inBind) {
          // Find the '=' sign, and make sure it's not "==", "!=", ">=", "<=", "+=", "-=", "*=", "/=", "%=", "&=", "^=", "|="
          int eq = i;
          final int sc = script.indexOf(';', i + 1);
          while ((eq = script.indexOf('=', eq + 1)) != -1) {
            final char a = script.charAt(eq - 1);
            if (a != '=' && a != '!' && a != '>' && a != '<' && a != '+' && a != '-' && a != '*' && a != '/' && a != '%' && a != '&' && a != '^' && a != '!' && (eq == script.length() - 1 || script.charAt(eq + 1) != '='))
              break;
          }

          ++i;
          if (eq > sc)
            continue;

          if (eq != -1) {
            if (var == null) {
              final int colon = script.indexOf(':', i);
              var = script.substring(i, colon != -1 && colon < eq ? colon : eq).trim();
            }

            final String noop = var.startsWith("$") ? var : "null";
            builder.append(script.substring(i, eq)).append("= !cOmPaTiBlE ? ").append(noop).append(" : ");
            final int nl = script.indexOf('\n', eq + 1);
            i = sc != -1 && sc < nl ? sc : nl;
            // Reset the var if there is a semicolon
            if (i == sc)
              var = null;

            builder.append(script.substring(eq + 1, i == sc ? ++i : i).trim());
          }
          else {
            final int s = i;
            if ((i = script.indexOf('\n', i + 1)) == -1)
              i = script.length();

            builder.append(script.substring(s, i));
          }

          continue;
        }

        if (m < 0)
          i = -m - 1;
      }

      ++i;
    }

    return out.toString();
  }
}