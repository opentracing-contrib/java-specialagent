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

public class ReferralScanner {
  private static final Logger logger = Logger.getLogger(ReferralScanner.class.getName());

  static class Referral implements Serializable {
    private static final long serialVersionUID = -6621289721092300709L;
    private final String className;
    private final Set<String> fields = new HashSet<>();
    private final Map<String,List<String[]>> methods = new HashMap<>();

    Referral(final String className) {
      this.className = className;
    }

    void addMethod(final String name, final String[] parameters) {
      List<String[]> signatures = methods.get(name);
      if (signatures == null)
        methods.put(name, signatures = new ArrayList<>());

      signatures.add(parameters);
    }

    void addField(final String name) {
      fields.add(name);
    }

    boolean hasField(final String name) {
      return fields.contains(name);
    }

    boolean hasMethod(final String name, final String[] parameters) {
      final List<String[]> signatures = methods.get(name);
      if (signatures == null)
        return false;

      for (final String[] signature : signatures)
        if (Arrays.equals(signature, parameters))
          return true;

      return false;
    }
  }

  static class Manifest implements Serializable {
    private static final long serialVersionUID = -6324404954794150472L;
    private final Map<String,Referral> referrals = new HashMap<>();

    public Referral addClass(final Type type) {
      final String className = type.getClassName();
      Referral referral = referrals.get(className);
      if (referral == null)
        referrals.put(className, referral = new Referral(className));

      return referral;
    }

    public Referral addField(final Type type, final String name) {
      final Referral referral = addClass(type);
      referral.addField(name);
      return referral;
    }

    public Referral addMethod(final Type type, final String name, final Type[] parameters) {
      final Referral referral = addClass(type);
      final String[] args = new String[parameters.length];
      for (int i = 0; i < args.length; ++i)
        args[i] = parameters[i].getClassName();

      referral.addMethod(name, args);
      return referral;
    }

    public Referral getReferral(final String className) {
      return referrals.get(className);
    }
  }

  private static class ReferralVisitor extends ClassVisitor {
    private final Manifest referrals;

    public ReferralVisitor(final Manifest referrals) {
      super(Opcodes.ASM5);
      this.referrals = referrals;
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
      if (logger.isLoggable(Level.FINEST))
        logger.finest(name + " " + desc);

      return new MethodVisitor(Opcodes.ASM5) {
        @Override
        public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
          referrals.addClass(Type.getType(desc));
          return super.visitAnnotation(desc, visible);
        }

        @Override
        public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
          referrals.addField(Type.getType(desc), name);
          super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {
          final Type[] types = Type.getArgumentTypes(desc);
          referrals.addMethod(Type.getObjectType(owner), name, types);
          super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitTypeInsn(final int opcode, final String type) {
          referrals.addClass(Type.getObjectType(type));
          super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitLocalVariable(final String name, final String desc, final String signature, final Label start, final Label end, final int index) {
          referrals.addClass(Type.getType(desc));
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
  }

  private final ReferralVisitor referralVisitor;

  ReferralScanner(final Manifest referrals) {
    this.referralVisitor = new ReferralVisitor(referrals);
  }

  public void scanReferrals(final URL[] urls) throws IOException {
    final URLClassLoader classLoader = new URLClassLoader(urls, null);
    for (final URL url : classLoader.getURLs()) {
      if (url.getPath().endsWith(".jar")) {
        try (final ZipInputStream in = new ZipInputStream(url.openStream())) {
          for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
            final String name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info"))
              scanReferrals(classLoader, name);
          }
        }
      }
      else {
        final File file = new File(url.getPath());
        final Path path = file.toPath();
        Util.recurseDir(file, new Predicate<File>() {
          @Override
          public boolean test(final File t) {
            if (t.isDirectory())
              return true;

            final String name = path.relativize(t.toPath()).toString();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              try {
                scanReferrals(classLoader, name);
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
  }

  public void scanReferrals(final ClassLoader classLoader, final String classResource) throws IOException {
    try (final InputStream in = classLoader.getResourceAsStream(classResource)) {
      new ClassReader(in).accept(referralVisitor, 0);
    }
  }

  public Manifest getReferrals() {
    return this.referralVisitor.referrals;
  }
}