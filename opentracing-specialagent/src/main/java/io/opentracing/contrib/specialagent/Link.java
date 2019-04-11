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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

class Link implements Serializable {
  private static final long serialVersionUID = -6621289721092300709L;
  private static final Logger logger = Logger.getLogger(Link.class.getName());

  private final String className;
  private final Set<String> fields = new HashSet<>();
  private final Map<String,List<String[]>> methods = new HashMap<>();

  Link(final String className) {
    this.className = className;
  }

  private void addMethod(final String name, final String[] parameterTypes) {
    List<String[]> signatures = methods.get(name);
    if (signatures == null)
      methods.put(name, signatures = new ArrayList<>());

    signatures.add(parameterTypes);
  }

  private void addField(final String name) {
    fields.add(name);
  }

  String getClassName() {
    return this.className;
  }

  boolean hasField(final String name) {
    return fields.contains(name);
  }

  boolean hasMethod(final String name, final String[] parameterTypes) {
    final List<String[]> signatures = methods.get(name);
    if (signatures == null)
      return false;

    for (final String[] signature : signatures)
      if (Arrays.equals(signature, parameterTypes))
        return true;

    return false;
  }

  static class Manifest implements Serializable {
    private transient final ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5) {
      @Override
      public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        if (logger.isLoggable(Level.FINEST))
          logger.finest(name + " " + desc);

        return new MethodVisitor(Opcodes.ASM5) {
          @Override
          public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            addClass(Type.getType(desc));
            return super.visitAnnotation(desc, visible);
          }

          @Override
          public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
            addField(Type.getType(desc), name);
            super.visitFieldInsn(opcode, owner, name, desc);
          }

          @Override
          public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {
            final Type[] types = Type.getArgumentTypes(desc);
            addMethod(Type.getObjectType(owner), name, types);
            super.visitMethodInsn(opcode, owner, name, desc, itf);
          }

          @Override
          public void visitTypeInsn(final int opcode, final String type) {
            addClass(Type.getObjectType(type));
            super.visitTypeInsn(opcode, type);
          }

          @Override
          public void visitLocalVariable(final String name, final String desc, final String signature, final Label start, final Label end, final int index) {
            addClass(Type.getType(desc));
            // if (logger.isLoggable(Level.FINEST))
            // logger.finest(" visitLocalVariable(" + name + ", " + desc + ", " +
            // signature + ", " + start + ", " + end + ", " + index + ")");
            super.visitLocalVariable(name, desc, signature, start, end, index);
          }

          @Override
          public void visitInvokeDynamicInsn(final String name, final String desc, final Handle bsm, final Object ... bsmArgs) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitInvokeDynamicInsn(" + name + ", " + desc + ", ...)");

            super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
          }

          @Override
          public void visitMultiANewArrayInsn(final String desc, final int dims) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitMultiANewArrayInsn(" + desc + ", " + dims + ")");

            super.visitMultiANewArrayInsn(desc, dims);
          }

          @Override
          public void visitParameter(final String name, final int access) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitParameter(" + name + ", " + access + ")");

            super.visitParameter(name, access);
          }

          @Override
          public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitTypeAnnotation(" + typeRef + ", " + typePath + ", " + desc + ", " + visible + ")");

            return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
          }

          @Override
          public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc, final boolean visible) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitParameterAnnotation(" + parameter + ", " + desc + ", " + visible + ")");

            return super.visitParameterAnnotation(parameter, desc, visible);
          }

          @Override
          public void visitAttribute(final Attribute attr) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitAttribute(" + attr + ")");

            super.visitAttribute(attr);
          }

          @Override
          public void visitFrame(final int type, final int nLocal, final Object[] local, final int nStack, final Object[] stack) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitFrame(" + type + ", " + nLocal + ", " + nStack + ")");

            super.visitFrame(type, nLocal, local, nStack, stack);
          }

          @Override
          public void visitInsn(final int opcode) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitInsn(" + opcode + ")");

            super.visitInsn(opcode);
          }

          @Override
          public void visitIntInsn(final int opcode, final int operand) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitIntInsn(" + opcode + ", " + operand + ")");

            super.visitIntInsn(opcode, operand);
          }

          @Override
          public void visitVarInsn(final int opcode, final int var) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitVarInsn(" + opcode + ", " + var + ")");

            super.visitVarInsn(opcode, var);
          }

          @Override
          public void visitJumpInsn(final int opcode, final Label label) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitJumpInsn(" + opcode + ", " + label + ")");

            super.visitJumpInsn(opcode, label);
          }

          @Override
          public void visitLabel(final Label label) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitLabel(" + label + ")");

            super.visitLabel(label);
          }

          @Override
          public void visitLdcInsn(final Object cst) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitLdcInsn(" + cst + ")");

            super.visitLdcInsn(cst);
          }

          @Override
          public void visitIincInsn(final int var, final int increment) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitIincInsn(" + var + ", " + increment + ")");

            super.visitIincInsn(var, increment);
          }

          @Override
          public void visitTableSwitchInsn(final int min, final int max, final Label dflt, final Label ... labels) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitTableSwitchInsn(" + min + ", " + max + ", " + dflt + ", " + labels + ")");

            super.visitTableSwitchInsn(min, max, dflt, labels);
          }

          @Override
          public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitLookupSwitchInsn(" + dflt + ", " + keys + ", " + labels + ")");

            super.visitLookupSwitchInsn(dflt, keys, labels);
          }

          @Override
          public AnnotationVisitor visitInsnAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitInsnAnnotation(" + typeRef + ", " + typePath + ", " + desc + ", " + visible + ")");

            return super.visitInsnAnnotation(typeRef, typePath, desc, visible);
          }

          @Override
          public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String type) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitTryCatchBlock(" + start + ", " + end + ", " + handler + ", " + type + ")");

            super.visitTryCatchBlock(start, end, handler, type);
          }

          @Override
          public AnnotationVisitor visitTryCatchAnnotation(final int typeRef, final TypePath typePath, final String desc, final boolean visible) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitTryCatchAnnotation(" + typeRef + ", " + typePath + ", " + desc + ", " + visible + ")");

            return super.visitTryCatchAnnotation(typeRef, typePath, desc, visible);
          }

          @Override
          public AnnotationVisitor visitLocalVariableAnnotation(final int typeRef, final TypePath typePath, final Label[] start, final Label[] end, final int[] index, final String desc, final boolean visible) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitLocalVariableAnnotation(" + typeRef + ", " + typePath + ", " + start + ", " + end + ", " + index + ", " + desc + ", " + visible + ")");

            return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, visible);
          }

          @Override
          public void visitLineNumber(final int line, final Label start) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitLineNumber(" + line + ", " + start + ")");

            super.visitLineNumber(line, start);
          }

          @Override
          public void visitMaxs(final int maxStack, final int maxLocals) {
            if (logger.isLoggable(Level.FINEST))
              logger.finest("  visitMaxs(" + maxStack + ", " + maxLocals + ")");

            super.visitMaxs(maxStack, maxLocals);
          }
        };
      }
    };

    private static boolean isPrimitive(final Type type) {
      String name = type.getClassName();
      if (name.endsWith("[]"))
        name = name.substring(0, name.length() - 2);

      return "boolean".equals(name) || "byte".equals(name) || "char".equals(name) || "short".equals(name) || "int".equals(name) || "long".equals(name);
    }

    private static final long serialVersionUID = -6324404954794150472L;
    private static final String[] excludePrefixes = {"io.opentracing.", "java.", "javax.", "net.bytebuddy."};
    private final Map<String,Link> links = new HashMap<>();

    private Link addClass(final Type type) {
      if (isPrimitive(type))
        return null;

      final String className = type.getClassName();
      for (int i = 0; i < excludePrefixes.length; ++i)
        if (className.startsWith(excludePrefixes[i]))
          return null;

      Link link = links.get(className);
      if (link == null)
        links.put(className, link = new Link(className));

      return link;
    }

    private void addField(final Type type, final String name) {
      final Link link = addClass(type);
      if (link != null)
        link.addField(name);
    }

    private void addMethod(final Type type, final String name, final Type[] parameterTypes) {
      final Link link = addClass(type);
      if (link == null)
        return;

      final String[] parameters = new String[parameterTypes.length];
      for (int i = 0; i < parameters.length; ++i)
        parameters[i] = parameterTypes[i].getClassName();

      link.addMethod(name, parameters);
    }

    Link getLink(final String className) {
      return links.get(className);
    }

    void include(final ClassLoader classLoader, final String classResource) throws IOException {
      try (final InputStream in = classLoader.getResourceAsStream(classResource)) {
        new ClassReader(in).accept(classVisitor, 0);
      }
      catch (final IllegalArgumentException e) {
      }
    }

    @Override
    public String toString() {
      return SpecialAgentUtil.toIndentedString(links.keySet());
    }
  }

  public static Manifest createManifest(final URL[] urls) throws IOException {
    final Manifest manifest = new Manifest();
    final URLClassLoader classLoader = new URLClassLoader(urls, null);
    for (final URL url : classLoader.getURLs()) {
      if (url.getPath().endsWith(".jar")) {
        try (final ZipInputStream in = new ZipInputStream(url.openStream())) {
          for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
            final String name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info"))
              manifest.include(classLoader, name);
          }
        }
      }
      else {
        final File file = new File(url.getPath());
        final Path path = file.toPath();
        SpecialAgentUtil.recurseDir(file, new Predicate<File>() {
          @Override
          public boolean test(final File t) {
            if (t.isDirectory())
              return true;

            final String name = path.relativize(t.toPath()).toString();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              try {
                manifest.include(classLoader, name);
              }
              catch (final IOException e) {
                throw new IllegalStateException(e);
              }
            }

            return true;
          }
        });
      }
    }

    return manifest;
  }
}