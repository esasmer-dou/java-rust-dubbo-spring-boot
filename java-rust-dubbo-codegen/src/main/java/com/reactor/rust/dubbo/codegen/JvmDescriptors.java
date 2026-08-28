package com.reactor.rust.dubbo.codegen;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

final class JvmDescriptors {
    private JvmDescriptors() {
    }

    static String method(ExecutableType method, Elements elements) {
        StringBuilder descriptor = new StringBuilder("(");
        for (TypeMirror parameter : method.getParameterTypes()) {
            descriptor.append(type(parameter, elements));
        }
        return descriptor.append(')').append(type(method.getReturnType(), elements)).toString();
    }

    static String type(TypeMirror type, Elements elements) {
        if (type instanceof PrimitiveType primitive) {
            return switch (primitive.getKind()) {
                case BOOLEAN -> "Z";
                case BYTE -> "B";
                case SHORT -> "S";
                case INT -> "I";
                case LONG -> "J";
                case CHAR -> "C";
                case FLOAT -> "F";
                case DOUBLE -> "D";
                default -> throw new IllegalArgumentException("Unsupported primitive " + primitive);
            };
        }
        if (type instanceof NoType && type.getKind() == TypeKind.VOID) {
            return "V";
        }
        if (type instanceof ArrayType array) {
            return '[' + type(array.getComponentType(), elements);
        }
        if (type instanceof DeclaredType declared) {
            return 'L' + elements.getBinaryName((javax.lang.model.element.TypeElement) declared.asElement())
                    .toString().replace('.', '/') + ';';
        }
        throw new IllegalArgumentException("Unsupported contract type " + type + " (" + type.getKind() + ")");
    }
}
