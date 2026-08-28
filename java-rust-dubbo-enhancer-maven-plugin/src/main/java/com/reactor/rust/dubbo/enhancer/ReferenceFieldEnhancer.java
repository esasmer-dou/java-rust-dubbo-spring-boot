package com.reactor.rust.dubbo.enhancer;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class ReferenceFieldEnhancer {
    private static final String REFERENCE_DESCRIPTOR =
            "Lorg/apache/dubbo/config/annotation/DubboReference;";
    private static final String TARGET =
            "com/reactor/rust/dubbo/runtime/GeneratedDubboInjectionTarget";
    private static final String REGISTRY = "com/reactor/rust/dubbo/runtime/DubboClientRegistry";

    private ReferenceFieldEnhancer() {
    }

    static byte[] enhance(byte[] original) {
        Scan scan = new Scan();
        new ClassReader(original).accept(scan, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        if (scan.references.isEmpty() || scan.alreadyEnhanced) {
            return original;
        }
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new EnhancingVisitor(writer, scan), 0);
        return writer.toByteArray();
    }

    private static final class Scan extends ClassVisitor {
        private final List<ReferenceField> references = new ArrayList<>();
        private String className;
        private boolean alreadyEnhanced;

        private Scan() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            className = name;
            if (interfaces != null) {
                for (String current : interfaces) {
                    alreadyEnhanced |= TARGET.equals(current);
                }
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    if (!REFERENCE_DESCRIPTOR.equals(annotationDescriptor)) {
                        return null;
                    }
                    ReferenceField field = new ReferenceField(name, descriptor);
                    references.add(field);
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String key, Object annotationValue) {
                            if ("group".equals(key)) {
                                field.group = annotationValue.toString();
                            } else if ("version".equals(key)) {
                                field.version = annotationValue.toString();
                            }
                        }
                    };
                }
            };
        }
    }

    private static final class EnhancingVisitor extends ClassVisitor {
        private final Scan scan;

        private EnhancingVisitor(ClassVisitor visitor, Scan scan) {
            super(Opcodes.ASM9, visitor);
            this.scan = scan;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            String[] expanded = new String[(interfaces == null ? 0 : interfaces.length) + 1];
            if (interfaces != null) {
                System.arraycopy(interfaces, 0, expanded, 0, interfaces.length);
            }
            expanded[expanded.length - 1] = TARGET;
            super.visit(version, access, name, signature, superName, expanded);
        }

        @Override
        public void visitEnd() {
            MethodVisitor method = super.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                    "__rustDubboInject",
                    "(L" + REGISTRY + ";)V",
                    null,
                    null);
            method.visitCode();
            for (ReferenceField field : scan.references) {
                method.visitVarInsn(Opcodes.ALOAD, 0);
                method.visitVarInsn(Opcodes.ALOAD, 1);
                method.visitLdcInsn(Type.getType(field.descriptor).getClassName());
                method.visitLdcInsn(field.group);
                method.visitLdcInsn(field.version);
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, REGISTRY, "client",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                        false);
                method.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(field.descriptor).getInternalName());
                method.visitFieldInsn(Opcodes.PUTFIELD, scan.className, field.name, field.descriptor);
            }
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            super.visitEnd();
        }
    }

    private static final class ReferenceField {
        private final String name;
        private final String descriptor;
        private String group = "";
        private String version = "";

        private ReferenceField(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }
    }
}
