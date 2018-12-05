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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jboss.byteman.agent.Main;
import org.jboss.byteman.agent.Retransformer;
import org.jboss.byteman.rule.Rule;

import com.sun.tools.attach.VirtualMachine;

/**
 * Provides the Byteman manager implementation for OpenTracing.
 */
public class Agent {
  private static final Logger logger = Logger.getLogger(Agent.class.getName());

  static final String PLUGIN_ARG = "io.opentracing.contrib.specialagent.plugins";
  static final String DISABLE_LC = "io.opentracing.contrib.specialagent.disableLC";

  private static final String AGENT_RULES = "otarules.btm";
  private static final String DEPENDENCIES = "dependencies.tgf";

  private static final Map<ClassLoader,PluginClassLoader> classLoaderToPluginClassLoader = new IdentityHashMap<>();

  private static Retransformer retransformer;
  private static AllPluginsClassLoader allPluginsClassLoader;

  private static final Set<String> disabledLoadClasses;

  static {
    final String loggingConfig = System.getProperty("java.util.logging.config.file");
    if (loggingConfig != null) {
      try {
        LogManager.getLogManager().readConfiguration((loggingConfig.contains("file:/") ? new URL(loggingConfig) : new URL("file", "", loggingConfig)).openStream());
      }
      catch (final IOException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    final String disableLc = System.getProperty(DISABLE_LC);
    if (disableLc != null) {
      final String[] jarNames = disableLc.split(":");
      disabledLoadClasses = new HashSet<>(jarNames.length);
      for (final String jarName : jarNames)
        disabledLoadClasses.add(jarName);
    }
    else {
      disabledLoadClasses = null;
    }
  }

  private static Instrumentation instrumentation;

  public static void main(final String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: <PID>");
      System.exit(1);
    }

    final VirtualMachine vm = VirtualMachine.attach(args[0]);
    final String agentPath = Agent.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    try {
      vm.loadAgent(agentPath, null);
    }
    finally {
      vm.detach();
    }
  }

  public static void premain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    Agent.instrumentation = instrumentation;
    Main.premain(addManager(agentArgs), instrumentation);
  }

  public static void agentmain(final String agentArgs, final Instrumentation instrumentation) throws Exception {
    premain(agentArgs, instrumentation);
  }

  protected static String addManager(String agentArgs) {
    if (agentArgs == null || agentArgs.trim().isEmpty())
      agentArgs = "";
    else
      agentArgs += ",";

    agentArgs += "manager:" + Agent.class.getName();
    return agentArgs;
  }

  private static class AllPluginsClassLoader extends URLClassLoader {
    private final Set<URL> urls;

    public AllPluginsClassLoader(final Set<URL> urls) {
      super(urls.toArray(new URL[urls.size()]), new ClassLoader(null) {
        // This is overridden to ensure resources are not discovered in BootClassLoader
        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
          return null;
        }
      });
      this.urls = urls;
    }

    public boolean containsPath(final URL url) {
      return urls.contains(url);
    }
  }

  /**
   * This method initializes the manager.
   *
   * @param retransformer The ByteMan retransformer.
   */
  public static void initialize(final Retransformer retransformer) {
    Agent.retransformer = retransformer;

    if (logger.isLoggable(Level.FINEST))
      logger.finest("Agent#initialize() java.class.path:\n  " + System.getProperty("java.class.path").replace(File.pathSeparator, "\n  "));

    final Set<URL> pluginJarUrls = Util.findJarResources("META-INF/opentracing-specialagent/");
    final boolean runningFromTest = pluginJarUrls.isEmpty();
    if (runningFromTest) {
      if (logger.isLoggable(Level.FINE))
        logger.fine("Must be running from a test, because no JARs were found under META-INF/opentracing-specialagent/");

      try {
        final Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(AGENT_RULES);
        while (resources.hasMoreElements())
          pluginJarUrls.add(new URL(Util.getSourceLocation(resources.nextElement(), AGENT_RULES)));
      }
      catch (final IOException e) {
        throw new IllegalStateException(e);
      }

      final URL[] pluginPaths = Util.classPathToURLs(System.getProperty(PLUGIN_ARG));
      if (pluginPaths != null)
        for (final URL pluginPath : pluginPaths)
          pluginJarUrls.add(pluginPath);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("Loading " + pluginJarUrls.size() + " plugin paths:\n" + Util.toIndentedString(pluginJarUrls));

    // Override parent ClassLoader methods to avoid delegation of resource
    // resolution to BootLoader
    allPluginsClassLoader = new AllPluginsClassLoader(pluginJarUrls);

    int count = loadDependencies(allPluginsClassLoader);
    if (runningFromTest)
      count += loadDependencies(ClassLoader.getSystemClassLoader());

    if (count == 0)
      throw new IllegalStateException("Could not find " + DEPENDENCIES + " in any plugin JARs!");

    loadRules();
  }

  private static int loadDependencies(final ClassLoader classLoader) {
    int count = 0;
    try {
      final Enumeration<URL> enumeration = classLoader.getResources(DEPENDENCIES);
      final Set<String> dependencyUrls = new HashSet<>();

      while (enumeration.hasMoreElements()) {
        final URL dependencyUrl = enumeration.nextElement();
        if (dependencyUrls.contains(dependencyUrl.toString()))
          continue;

        dependencyUrls.add(dependencyUrl.toString());
        if (logger.isLoggable(Level.FINEST))
          logger.finest("Found " + DEPENDENCIES + ": " + dependencyUrl + " " + Util.getIdentityCode(dependencyUrl));

        final URL jarUrl = new URL(Util.getSourceLocation(dependencyUrl, DEPENDENCIES));
        final URL[] dependencies = Util.filterPluginURLs(allPluginsClassLoader.getURLs(), dependencyUrl);
        boolean foundReference = false;
        for (final URL dependency : dependencies) {
          if (allPluginsClassLoader.containsPath(dependency)) {
            // When run from a test, it may happen that both the "allPluginsClassLoader"
            // and SystemClassLoader have the same path, leading to the same dependencies.tgf
            // file to be processed twice. This check asserts the previously registered
            // dependencies are correct.
            final URL[] registered = pluginToDependencies.get(dependency);
            if (registered != null) {
              if (registered == pluginToDependencies.get(jarUrl))
                continue;

              throw new IllegalStateException("Dependencies already registered for " + dependency + " Are there multiple plugin JARs with " + DEPENDENCIES + " referencing the same plugin JAR? Offending JAR: " + jarUrl);
            }

            if (logger.isLoggable(Level.FINEST))
              logger.finest("Registering dependencies for " + jarUrl + " and " + dependency + ":" + Util.toIndentedString(dependencies));

            ++count;
            foundReference =  true;
            pluginToDependencies.put(jarUrl, dependencies);
            pluginToDependencies.put(dependency, dependencies);
          }
        }

        if (!foundReference)
          throw new IllegalStateException("Could not find a plugin JAR referenced in " + DEPENDENCIES + " of " + jarUrl);
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    return count;
  }

  private static final Map<URL,URL[]> pluginToDependencies = new HashMap<>();

  /**
   * This method loads any OpenTracing Agent rules (otarules.btm) found as
   * resources within the supplied classloader.
   */
  private static void loadRules() {
    if (allPluginsClassLoader == null) {
      logger.severe("Attempt to load OpenTracing agent rules before allPluginsClassLoader initialized");
      return;
    }

    if (retransformer == null) {
      logger.severe("Attempt to load OpenTracing agent rules before transformer initialized");
      return;
    }

    final List<String> scripts = new ArrayList<>();
    final List<String> scriptNames = new ArrayList<>();

    try {
      // Prepare the ClassLoader rule
      digestRule(ClassLoader.getSystemClassLoader().getResource("classloader.btm"), null, scripts, scriptNames);

      // Create map from plugin jar URL to its index in
      // allPluginsClassLoader.getURLs()
      final Map<String,Integer> pluginJarToIndex = new HashMap<>();
      for (int i = 0; i < allPluginsClassLoader.getURLs().length; ++i)
        pluginJarToIndex.put(allPluginsClassLoader.getURLs()[i].toString(), i);

      // Prepare the Plugin rules
      final Enumeration<URL> enumeration = allPluginsClassLoader.getResources(AGENT_RULES);
      while (enumeration.hasMoreElements()) {
        final URL scriptUrl = enumeration.nextElement();
        final String pluginJar = Util.getSourceLocation(scriptUrl, AGENT_RULES);
        if (logger.isLoggable(Level.FINEST))
          logger.finest("Dereferencing index for " + pluginJar);

        final int index = pluginJarToIndex.get(pluginJar);
        digestRule(scriptUrl, index, scripts, scriptNames);
      }

      loadScripts(scripts, scriptNames);
    }
    catch (final IOException e) {
      logger.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
    }

    if (logger.isLoggable(Level.FINE))
      logger.fine("OpenTracing Agent rules loaded");
  }

  /**
   * Loads the specified scripts with script names into Byteman.
   *
   * @param scripts The list of scripts.
   * @param scriptNames The list of script names.
   */
  private static void loadScripts(final List<String> scripts, final List<String> scriptNames) {
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
  private static void unloadRule(final String ruleName) {
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
   * {@link #retrofitScript(String,int)} to create a "load classes" script that
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
    final int s = header.indexOf("RULE ");
    final int e = header.indexOf('\n', s + 5);
    if (builder.length() > 0)
      builder.append('\n');

    builder.append(header.substring(0, e)).append(" (Load Classes)");
    builder.append(header.substring(e));
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
   * @return The script used to trigger the "load classes" procedure
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

  /**
   * Callback that is used to load a class by the specified resource path into
   * the specified {@code ClassLoader}.
   */
  private static final BiPredicate<String,ClassLoader> loadClass = new BiPredicate<String,ClassLoader>() {
    @Override
    public boolean test(final String path, final ClassLoader classLoader) {
      if (path.endsWith(".class")) {
        try {
          Class.forName(path.substring(0, path.length() - 6).replace('/', '.'), false, classLoader);
        }
        catch (final ClassNotFoundException e) {
          logger.log(Level.SEVERE, "Failed to load class", e);
        }
      }

      return true;
    }
  };

  private static boolean isLoadClassesDisabled(final URL pluginUrl) {
    if (disabledLoadClasses == null)
      return false;

    final String path = pluginUrl.toString();
    final String jarName = path.substring(path.lastIndexOf('/') + 1);
    return disabledLoadClasses.contains("*") || disabledLoadClasses.contains(jarName);
  }

  /**
   * Links the instrumentation plugin at the specified index. This method is
   * called by Byteman upon trigger of a rule from a otarules.btm script, and
   * its purpose is:
   * <ol>
   * <li>To link a plugin JAR to the {@code ClassLoader} in which the
   * instrumentation plugin is relevant (i.e. a {@code ClassLoader} which
   * contains the target classes of instrumentation).</li>
   * <li>To check if the instrumentation code is compatible with the classes
   * that are to be instrumented in the {@code ClassLoader}.</li>
   * <li>To return the value of the compatibility test, in order to allow the
   * rule to skip its logic in case the test does not pass.</li>
   * </ol>
   * The {@code index} is a reference to the array index of the plugin JAR's
   * {@code URL} in {@link #allPluginsClassLoader}, which is statically declared
   * during the script retrofit in {@link #retrofitScript(String,int)}.
   * <p>
   * The {@code args} parameter is used to obtain the caller object, which is
   * itself used to determine the {@code ClassLoader} in which the classes
   * relevant for instrumentation are being invoked, and are thus loaded. If the
   * caller object is null (meaning the triggered method is static), the
   * {@code cls} parameter is used to determine the target {@code ClassLoader}.
   * This method thereafter associates (in
   * {@link #classLoaderToPluginClassLoader}) a {@link PluginClassLoader} for
   * the instrumentation plugin at {@code index}. The association thereafter
   * allows the {@link #findClass(ClassLoader,String)} method to directly inject
   * the bytecode of the instrumentation classes into the target
   * {@code ClassLoader}.
   *
   * @param index The index of the plugin JAR's {@code URL} in
   *          {@link #allPluginsClassLoader}.
   * @param cls The class on which the trigger event occurred.
   * @param args The arguments used for the triggered method call.
   * @return {@code true} if the plugin at the specified index is compatible
   *         with its target classes in the invoking class's
   *         {@code ClassLoader}.
   * @see #retrofitScript(String,int)
   */
  @SuppressWarnings("resource")
  public static boolean linkPlugin(final int index, final Class<?> cls, final Object[] args) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest("linkPlugin(" + index + ", " + (cls != null ? cls.getName() + ".class" : "null") + ", " + Arrays.toString(args) + ")");

    Rule.disableTriggers();
    try {
      // Get the ClassLoader of the target class
      final Class<?> targetClass = args[0] != null ? args[0].getClass() : cls;
      final ClassLoader classLoader = targetClass.getClassLoader();

      // Find the Plugin Path (identified by index passed to this method)
      final URL pluginPath = allPluginsClassLoader.getURLs()[index];
      if (logger.isLoggable(Level.FINEST))
        logger.finest("  Plugin Path: " + pluginPath);

      // Now find all the paths that pluginPath depends on, by reading dependencies.tgf
      final URL[] pluginPaths = pluginToDependencies.get(pluginPath);
      if (pluginPaths == null)
        throw new IllegalStateException("No " + DEPENDENCIES + " was registered for: " + pluginPath);

      if (logger.isLoggable(Level.FINEST))
        logger.finest("new " + PluginClassLoader.class.getSimpleName() + "([\n" + Util.toIndentedString(pluginPaths) + "]\n, " + Util.getIdentityCode(classLoader) + ");");

      // Create an isolated (no parent ClassLoader) URLClassLoader with the pluginPaths
      final PluginClassLoader pluginClassLoader = new PluginClassLoader(pluginPaths, classLoader);
      if (!pluginClassLoader.isCompatible(classLoader)) {
        try {
          pluginClassLoader.close();
        }
        catch (final IOException e) {
          logger.log(Level.WARNING, "Failed to close " + PluginClassLoader.class.getSimpleName() + ": " + Util.getIdentityCode(pluginClassLoader), e);
        }

        return false;
      }

      if (classLoader == null) {
        if (logger.isLoggable(Level.FINE))
          logger.fine("Target classLoader is the BootClassLoader, so adding plugin JARs to the bootstrap classpath directly");

        for (final URL path : pluginPaths) {
          try {
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(path.getPath()));
          }
          catch (final IOException e) {
            logger.log(Level.SEVERE, "Failed to add path to BootClassLoader classpath: " + path.getPath(), e);
          }
        }
      }
      else if (classLoader == ClassLoader.getSystemClassLoader()) {
        if (logger.isLoggable(Level.FINE))
          logger.fine("Target classLoader is the SystemClassLoader, so adding plugin JARs to the system classpath directly");

        for (final URL path : pluginPaths) {
          try {
            instrumentation.appendToSystemClassLoaderSearch(new JarFile(path.getPath()));
          }
          catch (final IOException e) {
            logger.log(Level.SEVERE, "Failed to add path to SystemClassLoader classpath: " + path.getPath(), e);
          }
        }
      }

      if (!isLoadClassesDisabled(pluginPath)) {
        // Associate the pluginClassLoader with the target class's classLoader
        classLoaderToPluginClassLoader.put(classLoader, pluginClassLoader);

        // Enable triggers to the LoadClasses script can execute
        Rule.enableTriggers();

        // Call Class.forName(...) for each class in pluginClassLoader to load in
        // the caller's classLoader
        for (final URL pathUrl : pluginClassLoader.getURLs()) {
          if (pathUrl.toString().endsWith(".jar")) {
            try (final ZipInputStream zip = new ZipInputStream(pathUrl.openStream())) {
              for (ZipEntry entry; (entry = zip.getNextEntry()) != null; loadClass.test(entry.getName(), classLoader));
            }
            catch (final IOException e) {
              logger.log(Level.SEVERE, "Failed to read from JAR: " + pathUrl, e);
            }
          }
          else {
            final File dir = new File(URI.create(pathUrl.toString()));
            final Path path = dir.toPath();
            Util.recurseDir(dir, new Predicate<File>() {
              @Override
              public boolean test(final File file) {
                loadClass.test(path.relativize(file.toPath()).toString(), classLoader);
                return true;
              }
            });
          }
        }
      }
      else if (logger.isLoggable(Level.FINEST)) {
        logger.finest("  LoadClasses is disabled for: " + pluginPath);
      }

      return true;
    }
    finally {
      Rule.enableTriggers();
    }
  }

  /**
   * Returns the bytecode of the {@code Class} by the name of {@code name}, if
   * the {@code classLoader} matched a plugin {@code ClassLoader} that contains
   * OpenTracing instrumentation classes intended to be loaded into
   * {@code classLoader}. This method is called by the "OpenTracing ClassLoader
   * Injection" Byteman script that is triggered by
   * {@link ClassLoader#findClass(String)}. This method returns {@code null} if
   * it cannot locate the bytecode for the requested {@code Class}, or if it has
   * already been called for {@code classLoader} and {@code name}.
   *
   * @param classLoader The {@code ClassLoader} to match to a plugin
   *          {@code ClassLoader} that contains OpenTracing instrumentation
   *          classes intended to be loaded into {@code classLoader}.
   * @param name The name of the {@code Class} to be found.
   * @return The bytecode of the {@code Class} by the name of {@code name}, or
   *         {@code null} if this method has already been called for
   *         {@code classLoader} and {@code name}.
   */
  public static byte[] findClass(final ClassLoader classLoader, final String name) {
    Rule.disableTriggers();
    try {
      if (logger.isLoggable(Level.FINEST))
        logger.finest(">>>>>>>> findClass(" + Util.getIdentityCode(classLoader) + ", \"" + name + "\")");

      // Check if the classLoader matches a pluginClassLoader
      final PluginClassLoader pluginClassLoader = classLoaderToPluginClassLoader.get(classLoader);
      if (pluginClassLoader == null)
        return null;

      // Check that the resourceName has not already been retrieved by this method
      // (this may be a moot check, because the JVM won't call findClass() twice
      // for the same class)
      final String resourceName = name.replace('.', '/').concat(".class");
      if (pluginClassLoader.markFindResource(classLoader, resourceName))
        return null;

      // Return the resource's bytes, or null if the resource does not exist in
      // pluginClassLoader
      try (final InputStream in = pluginClassLoader.getResourceAsStream(resourceName)) {
        return in == null ? null : Util.readBytes(in);
      }
      catch (final IOException e) {
        logger.log(Level.SEVERE, "Failed to read bytes for " + resourceName, e);
        return null;
      }
    }
    finally {
      Rule.enableTriggers();
    }
  }
}