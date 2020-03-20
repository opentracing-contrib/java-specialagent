package io.opentracing.contrib.specialagent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

import net.bytebuddy.agent.ByteBuddyAgent;

public class Elevator {
  private static Instrumentation inst;

  private static JarFile createJarFileOfSource(final Class<?> cls) throws IOException {
    return createJarFileOfSource(new File(cls.getProtectionDomain().getCodeSource().getLocation().getPath()));
  }

  private static JarFile createJarFileOfSource(final File file) throws IOException {
    final String path = file.getAbsolutePath();
    if (file.isDirectory()) {
      if ("classes".equals(file.getName()))
        return SpecialAgentUtil.createTempJarFile(file);

      if ("test-classes".equals(file.getName()))
        return SpecialAgentUtil.createTempJarFile(new File(file.getParent(), "classes"));
    }
    else {
      if (path.endsWith(".jar"))
        return new JarFile(file);

      if (path.endsWith("-tests.jar"))
        return new JarFile(new File(path.substring(0, path.length() - 10) + ".jar"));
    }

    throw new UnsupportedOperationException("Unsupported source path: " + path);
  }

  private static JarFile[] appendSourceLocationToBootstrap(final Class<?> cls) throws IOException {
    return new JarFile[] {createJarFileOfSource(cls)};
  }

  private static JarFile[] appendSourceLocationToBootstrap(final File ... files) throws IOException {
    final JarFile[] jarFiles = new JarFile[files.length + 1];
    jarFiles[0] = createJarFileOfSource(Elevator.class);
    for (int i = 0; i < files.length; ++i)
      jarFiles[i + 1] = createJarFileOfSource(files[i]);

    return jarFiles;
  }

  static Instrumentation install(final File[] bootstrapFiles) {
    if (inst != null)
      return inst;

    try {
//      if (logger.isLoggable(Level.FINE))
//        logger.fine("\n>>>>>>>>>>>>>>>>>>>>>>> Installing Agent <<<<<<<<<<<<<<<<<<<<<<<\n");

      final JarFile[] jarFiles = bootstrapFiles != null ? appendSourceLocationToBootstrap(bootstrapFiles) : appendSourceLocationToBootstrap(Elevator.class);
//      if (logger.isLoggable(Level.FINE))
//        logger.fine("\n================== Installing BootLoaderAgent ==================\n");

      final Instrumentation inst = ByteBuddyAgent.install();
      for (final JarFile jarFile : jarFiles) {
        System.err.println("Adding jar to bootstrap: " + jarFile.getName());
        inst.appendToBootstrapClassLoaderSearch(jarFile);
      }

      BootLoaderAgent.premain(inst, jarFiles);

      if (BootProxyClassLoader.INSTANCE.loadClassOrNull("io.opentracing.contrib.specialagent.Level", false) == null)
        throw new IllegalStateException();

      if (BootProxyClassLoader.INSTANCE.getResource("io/opentracing/contrib/specialagent/Adapter.class") == null)
        throw new IllegalStateException();

      return Elevator.inst = inst;
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }
}