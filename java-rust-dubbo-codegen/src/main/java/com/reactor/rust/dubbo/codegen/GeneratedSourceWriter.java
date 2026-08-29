package com.reactor.rust.dubbo.codegen;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

final class GeneratedSourceWriter {
    private static final String GENERATED_PACKAGE = "com.reactor.rust.dubbo.generated";

    private final ProcessingEnvironment processing;
    private final Elements elements;
    private final Types types;
    private final List<RustDubboProcessor.ReferenceContract> references;
    private final List<RustDubboProcessor.ServiceContract> services;
    private final CodecRegistry codecs;

    GeneratedSourceWriter(ProcessingEnvironment processing,
                          Collection<RustDubboProcessor.ReferenceContract> references,
                          Collection<RustDubboProcessor.ServiceContract> services) {
        this.processing = processing;
        elements = processing.getElementUtils();
        types = processing.getTypeUtils();
        this.references = references.stream()
                .sorted(Comparator.comparing(RustDubboProcessor.ReferenceContract::contract)
                        .thenComparing(RustDubboProcessor.ReferenceContract::group)
                        .thenComparing(RustDubboProcessor.ReferenceContract::version))
                .toList();
        this.services = services.stream()
                .sorted(Comparator.comparing(RustDubboProcessor.ServiceContract::implementation))
                .toList();
        codecs = new CodecRegistry();
    }

    void write() throws IOException {
        registerContractTypes();
        String fingerprint = fingerprint();
        String simpleName = "RustDubboGeneratedModule_" +
                Integer.toUnsignedString(StableIds.fnv1a32(fingerprint), 16);
        String qualifiedName = GENERATED_PACKAGE + '.' + simpleName;
        JavaFileObject source = processing.getFiler().createSourceFile(qualifiedName);
        try (Writer writer = source.openWriter()) {
            writer.write(source(simpleName));
        }
        var imports = processing.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        try (Writer writer = imports.openWriter()) {
            writer.write(qualifiedName);
            writer.write('\n');
        }
    }

    private void registerContractTypes() throws IOException {
        for (RustDubboProcessor.ReferenceContract reference : references) {
            for (RustDubboProcessor.MethodContract method : reference.methods()) {
                registerMethodTypes(method);
            }
        }
        for (RustDubboProcessor.ServiceContract service : services) {
            for (RustDubboProcessor.MethodContract method : service.methods()) {
                registerMethodTypes(method);
            }
        }
    }

    private void registerMethodTypes(RustDubboProcessor.MethodContract method) throws IOException {
        ExecutableType signature = method.type();
        for (TypeMirror parameter : signature.getParameterTypes()) {
            codecs.register(parameter, method.element());
        }
        if (signature.getReturnType().getKind() != TypeKind.VOID) {
            if (isErasure(signature.getReturnType(), "java.util.concurrent.CompletableFuture")) {
                TypeMirror valueType = futureValueType(signature.getReturnType(), method.element());
                if (!isErasure(valueType, "java.lang.Void")) {
                    codecs.register(valueType, method.element());
                }
            } else {
                codecs.register(signature.getReturnType(), method.element());
            }
        }
    }

    private String source(String simpleName) throws IOException {
        StringBuilder source = new StringBuilder(32_768);
        line(source, "package " + GENERATED_PACKAGE + ";");
        line(source, "");
        line(source, "@org.springframework.boot.autoconfigure.AutoConfiguration(before = "
                + "com.reactor.rust.dubbo.spring.RustDubboAutoConfiguration.class)");
        line(source, "public final class " + simpleName
                + " implements com.reactor.rust.dubbo.runtime.GeneratedDubboModule {");
        emitProviderBeans(source);
        emitRegisterClients(source);
        emitRegisterProviders(source);
        emitClients(source);
        emitDispatchers(source);
        codecs.emit(source);
        line(source, "}");
        return source.toString();
    }

    private void emitProviderBeans(StringBuilder source) throws IOException {
        for (RustDubboProcessor.ServiceContract service : services) {
            TypeElement implementation = service.implementationElement();
            List<ExecutableElement> constructors = implementation.getEnclosedElements().stream()
                    .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                    .map(element -> (ExecutableElement) element)
                    .filter(constructor -> constructor.getModifiers().contains(Modifier.PUBLIC))
                    .toList();
            ExecutableElement selected = constructors.stream()
                    .filter(constructor -> constructor.getParameters().isEmpty())
                    .findFirst().orElse(constructors.size() == 1 ? constructors.get(0) : null);
            boolean implicit = implementation.getEnclosedElements().stream()
                    .noneMatch(element -> element.getKind() == ElementKind.CONSTRUCTOR);
            if (!implicit && selected == null) {
                fail(implementation, "@DubboService requires one public constructor or a public no-arg "
                        + "constructor for reflection-free Spring bean generation");
            }
            String method = "rustDubboProvider_" + Integer.toUnsignedString(
                    StableIds.fnv1a32(service.implementation()), 16);
            line(source, "  @org.springframework.context.annotation.Bean");
            line(source, "  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean("
                    + service.implementation() + ".class)");
            String parameters = selected == null ? "" : constructorParameters(selected);
            line(source, "  public " + service.implementation() + ' ' + method + '(' + parameters + ") {");
            String arguments = selected == null ? "" : argumentNames(selected.getParameters().size());
            line(source, "    return new " + service.implementation() + '(' + arguments + ");");
            line(source, "  }");
            line(source, "");
        }
    }

    private void emitRegisterClients(StringBuilder source) {
        line(source, "  @Override");
        line(source, "  public void registerClients(");
        line(source, "      com.reactor.rust.dubbo.runtime.DubboClientRegistry clients,");
        line(source, "      com.reactor.rust.dubbo.runtime.NativeDubboRuntime runtime) {");
        Map<String, RustDubboProcessor.ReferenceContract> unique = new LinkedHashMap<>();
        for (RustDubboProcessor.ReferenceContract reference : references) {
            unique.putIfAbsent(clientKey(reference), reference);
        }
        int index = 0;
        for (RustDubboProcessor.ReferenceContract reference : unique.values()) {
            String id = "clientId" + index++;
            boolean startupCheck = references.stream()
                    .filter(candidate -> clientKey(candidate).equals(clientKey(reference)))
                    .anyMatch(RustDubboProcessor.ReferenceContract::check);
            line(source, "    int " + id + " = runtime.createClient(\"" + escape(reference.contract())
                    + "\", \"" + escape(reference.group()) + "\", \""
                    + escape(reference.version()) + "\", " + startupCheck + ");");
            for (RustDubboProcessor.MethodContract method : reference.methods()) {
                line(source, "    runtime.registerMethod(" + id + ", " + method.id() + ", \""
                        + escape(method.name()) + "\", \"" + escape(method.descriptor()) + "\");");
            }
            line(source, "    clients.register(new com.reactor.rust.dubbo.runtime.DubboClientKey(\""
                    + escape(reference.contract()) + "\", \"" + escape(reference.group())
                    + "\", \"" + escape(reference.version()) + "\"), new "
                    + clientClass(reference) + '(' + id + ", runtime.clientOptions(\""
                    + escape(reference.contract()) + "\", \"" + escape(reference.group())
                    + "\", \"" + escape(reference.version()) + "\")));");
        }
        line(source, "  }");
        line(source, "");
    }

    private void emitRegisterProviders(StringBuilder source) {
        line(source, "  @Override");
        line(source, "  public void registerProviders(");
        line(source, "      com.reactor.rust.dubbo.runtime.DubboProviderRegistry providers,");
        line(source, "      com.reactor.rust.dubbo.runtime.NativeDubboRuntime runtime,");
        line(source, "      com.reactor.rust.dubbo.runtime.DubboBeanResolver beans) {");
        for (RustDubboProcessor.ServiceContract service : services) {
            if (!service.export()) {
                continue;
            }
            int serviceId = serviceId(service.contract(), service.group(), service.version());
            String dispatcher = dispatcherClass(service);
            line(source, "    providers.register(" + serviceId + ", new " + dispatcher
                    + "(beans.require(" + service.implementation() + ".class), "
                    + "runtime.clientOptions().maxCollectionItems()));");
            for (RustDubboProcessor.MethodContract method : service.methods()) {
                line(source, "    runtime.registerProviderMethod(" + serviceId + ", " + method.id()
                        + ", \"" + escape(service.contract()) + "\", \""
                        + escape(service.group()) + "\", \"" + escape(service.version())
                        + "\", \"" + escape(method.name()) + "\", \""
                        + escape(method.descriptor()) + "\", \""
                        + escape(service.executor()) + "\", " + service.executes() + ");");
            }
        }
        line(source, "  }");
        line(source, "");
    }

    private void emitClients(StringBuilder source) {
        Map<String, RustDubboProcessor.ReferenceContract> unique = new LinkedHashMap<>();
        for (RustDubboProcessor.ReferenceContract reference : references) {
            unique.putIfAbsent(clientKey(reference), reference);
        }
        for (RustDubboProcessor.ReferenceContract reference : unique.values()) {
            line(source, "  private static final class " + clientClass(reference)
                    + " extends com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport"
                    + " implements " + reference.contract() + " {");
            line(source, "    private " + clientClass(reference)
                    + "(int clientId, com.reactor.rust.dubbo.runtime.DubboClientOptions options) {");
            line(source, "      super(clientId, options.timeoutMs(), options.maxCollectionItems(),"
                    + " options.initialBufferBytes(), options.retainedBuffers(),"
                    + " options.maxRetainedBufferBytes());");
            line(source, "    }");
            for (RustDubboProcessor.MethodContract method : reference.methods()) {
                emitClientMethod(source, method);
            }
            line(source, "  }");
            line(source, "");
        }
    }

    private void emitClientMethod(StringBuilder source, RustDubboProcessor.MethodContract method) {
        ExecutableType signature = method.type();
        String returnType = signature.getReturnType().toString();
        line(source, "    @Override");
        line(source, "    public " + returnType + ' ' + method.name() + '('
                + parameters(signature) + ") {");
        line(source, "      try (com.reactor.rust.dubbo.runtime.DubboCallBuffer call = acquireCallBuffer()) {");
        line(source, "        com.reactor.rust.dubbo.runtime.Hessian2Output out = call.output();");
        for (int index = 0; index < signature.getParameterTypes().size(); index++) {
            line(source, "        " + codecs.writeCall(signature.getParameterTypes().get(index),
                    "out", "arg" + index) + ';');
        }
        if (isErasure(signature.getReturnType(), "java.util.concurrent.CompletableFuture")) {
            TypeMirror valueType;
            try {
                valueType = futureValueType(signature.getReturnType(), method.element());
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
            line(source, "        return invokeAsync(" + method.id() + ", call, in -> {");
            line(source, "          int responseFlag = requireValueResponse(in);");
            if (isErasure(valueType, "java.lang.Void")) {
                line(source, "          return null;");
            } else {
                line(source, "          if (isNullResponse(responseFlag)) return null;");
                line(source, "          return " + codecs.readCall(valueType, "in") + ';');
            }
            line(source, "        });");
            line(source, "      }");
            line(source, "    }");
            return;
        }
        line(source, "        try (com.reactor.rust.dubbo.runtime.NativeResponse response = invoke("
                + method.id() + ", call)) {");
        line(source, "          com.reactor.rust.dubbo.runtime.Hessian2Input in = responseInput(response, call);");
        line(source, "          int responseFlag = requireValueResponse(in);");
        if (signature.getReturnType().getKind() == TypeKind.VOID) {
            line(source, "          return;");
        } else {
            if (signature.getReturnType().getKind().isPrimitive()) {
                line(source, "          if (isNullResponse(responseFlag)) {");
                line(source, "            throw new com.reactor.rust.dubbo.runtime.DubboCodecException("
                        + "\"Provider returned null for primitive result\");");
                line(source, "          }");
            } else {
                line(source, "          if (isNullResponse(responseFlag)) return null;");
            }
            line(source, "          return " + codecs.readCall(signature.getReturnType(), "in") + ';');
        }
        line(source, "        }");
        line(source, "      }");
        line(source, "    }");
    }

    private void emitDispatchers(StringBuilder source) {
        for (RustDubboProcessor.ServiceContract service : services) {
            if (!service.export()) {
                continue;
            }
            int serviceId = serviceId(service.contract(), service.group(), service.version());
            line(source, "  private static final class " + dispatcherClass(service)
                    + " extends com.reactor.rust.dubbo.runtime.GeneratedDubboProviderSupport {");
            line(source, "    private final " + service.implementation() + " target;");
            line(source, "    private " + dispatcherClass(service) + '(' + service.implementation()
                    + " target, int maxCollectionItems) {");
            line(source, "      super(maxCollectionItems);");
            line(source, "      this.target = target;");
            line(source, "    }");
            line(source, "    @Override");
            line(source, "    public int dispatch(int serviceId, int methodId, java.nio.ByteBuffer request,"
                    + " int requestLength, long responseHandle, java.nio.ByteBuffer response) {");
            line(source, "      if (serviceId != " + serviceId + ") throw new IllegalArgumentException("
                    + "\"Unexpected service id \" + serviceId);");
            line(source, "      com.reactor.rust.dubbo.runtime.Hessian2Input in = requestInput(request, requestLength);");
            line(source, "      com.reactor.rust.dubbo.runtime.Hessian2Output out = responseOutput(responseHandle, response);");
            line(source, "      try {");
            line(source, "        switch (methodId) {");
            for (RustDubboProcessor.MethodContract method : service.methods()) {
                emitDispatchCase(source, method);
            }
            line(source, "          default -> throw new IllegalArgumentException(\"Unknown method id \" + methodId);");
            line(source, "        }");
            line(source, "      } catch (RuntimeException exception) {");
            line(source, "        throw exception;");
            line(source, "      } catch (Exception exception) {");
            line(source, "        throw new com.reactor.rust.dubbo.runtime.DubboNativeException("
                    + "\"Dubbo provider business method failed\", exception);");
            line(source, "      }");
            line(source, "      return out.position();");
            line(source, "    }");
            line(source, "  }");
            line(source, "");
        }
    }

    private void emitDispatchCase(StringBuilder source, RustDubboProcessor.MethodContract method) {
        ExecutableType signature = method.type();
        line(source, "          case " + method.id() + " -> {");
        for (int index = 0; index < signature.getParameterTypes().size(); index++) {
            TypeMirror type = signature.getParameterTypes().get(index);
            line(source, "            " + type + " arg" + index + " = "
                    + codecs.readCall(type, "in") + ';');
        }
        String arguments = argumentNames(signature.getParameterTypes().size());
        if (isErasure(signature.getReturnType(), "java.util.concurrent.CompletableFuture")) {
            TypeMirror valueType;
            try {
                valueType = futureValueType(signature.getReturnType(), method.element());
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
            line(source, "            " + signature.getReturnType() + " future = target."
                    + method.name() + '(' + arguments + ");");
            line(source, "            if (future == null) {");
            line(source, "              com.reactor.rust.dubbo.runtime.NativeDubboBridge.failProviderResponse("
                    + "responseHandle, \"Provider returned a null CompletableFuture\");");
            line(source, "              return ASYNC_PENDING;");
            line(source, "            }");
            line(source, "            if (future.isDone()) {");
            if (isErasure(valueType, "java.lang.Void")) {
                line(source, "              future.join();");
                line(source, "              out.writeInt("
                        + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_NULL_VALUE);");
            } else {
                line(source, "              " + valueType + " value = future.join();");
                line(source, "              if (value == null) {");
                line(source, "                out.writeInt("
                        + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_NULL_VALUE);");
                line(source, "              } else {");
                line(source, "                out.writeInt("
                        + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_VALUE);");
                line(source, "                " + codecs.writeCall(valueType, "out", "value") + ';');
                line(source, "              }");
            }
            line(source, "            } else {");
            line(source, "              future.whenComplete((value, error) -> {");
            line(source, "              if (error != null) {");
            line(source, "                com.reactor.rust.dubbo.runtime.NativeDubboBridge.failProviderResponse("
                    + "responseHandle, error.toString());");
            line(source, "                return;");
            line(source, "              }");
            line(source, "              try {");
            line(source, "                com.reactor.rust.dubbo.runtime.Hessian2Output asyncOut = "
                    + "new com.reactor.rust.dubbo.runtime.Hessian2Output();");
            line(source, "                asyncOut.attach(com.reactor.rust.dubbo.runtime.NativeDubboBridge."
                    + "growProviderResponse(responseHandle, 1), responseHandle);");
            if (isErasure(valueType, "java.lang.Void")) {
                line(source, "                asyncOut.writeInt("
                        + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_NULL_VALUE);");
            } else {
                line(source, "                if (value == null) {");
                line(source, "                  asyncOut.writeInt("
                        + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_NULL_VALUE);");
                line(source, "                } else {");
                line(source, "                  asyncOut.writeInt("
                        + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_VALUE);");
                line(source, "                  " + codecs.writeCall(valueType, "asyncOut", "value") + ';');
                line(source, "                }");
            }
            line(source, "                com.reactor.rust.dubbo.runtime.NativeDubboBridge."
                    + "completeProviderResponse(responseHandle, asyncOut.position());");
            line(source, "              } catch (Throwable encodingError) {");
            line(source, "                com.reactor.rust.dubbo.runtime.NativeDubboBridge.failProviderResponse("
                    + "responseHandle, encodingError.toString());");
            line(source, "              }");
            line(source, "              });");
            line(source, "              return ASYNC_PENDING;");
            line(source, "            }");
        } else if (signature.getReturnType().getKind() == TypeKind.VOID) {
            line(source, "            target." + method.name() + '(' + arguments + ");");
            line(source, "            out.writeInt("
                    + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_NULL_VALUE);");
        } else if (signature.getReturnType().getKind().isPrimitive()) {
            line(source, "            " + signature.getReturnType() + " value = target."
                    + method.name() + '(' + arguments + ");");
            line(source, "            out.writeInt("
                    + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_VALUE);");
            line(source, "            " + codecs.writeCall(signature.getReturnType(), "out", "value") + ';');
        } else {
            line(source, "            " + signature.getReturnType() + " value = target."
                    + method.name() + '(' + arguments + ");");
            line(source, "            if (value == null) {");
            line(source, "              out.writeInt("
                    + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_NULL_VALUE);");
            line(source, "            } else {");
            line(source, "              out.writeInt("
                    + "com.reactor.rust.dubbo.runtime.GeneratedDubboClientSupport.RESPONSE_VALUE);");
            line(source, "              " + codecs.writeCall(signature.getReturnType(), "out", "value") + ';');
            line(source, "            }");
        }
        line(source, "          }");
    }

    private String parameters(ExecutableType signature) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < signature.getParameterTypes().size(); index++) {
            if (index > 0) value.append(", ");
            value.append(signature.getParameterTypes().get(index)).append(" arg").append(index);
        }
        return value.toString();
    }

    private static String constructorParameters(ExecutableElement constructor) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < constructor.getParameters().size(); index++) {
            if (index > 0) value.append(", ");
            value.append(constructor.getParameters().get(index).asType())
                    .append(" arg").append(index);
        }
        return value.toString();
    }

    private static String argumentNames(int count) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) value.append(", ");
            value.append("arg").append(index);
        }
        return value.toString();
    }

    private String fingerprint() {
        StringBuilder value = new StringBuilder();
        for (RustDubboProcessor.ReferenceContract reference : references) {
            value.append('R').append(clientKey(reference)).append(reference.check());
        }
        for (RustDubboProcessor.ServiceContract service : services) {
            value.append('S').append(service.implementation()).append(service.contract())
                    .append(service.group()).append(service.version()).append(service.executor())
                    .append(service.async()).append(service.export()).append(service.executes());
        }
        return value.toString();
    }

    private static String clientKey(RustDubboProcessor.ReferenceContract reference) {
        return reference.contract() + '|' + reference.group() + '|' + reference.version();
    }

    private static int serviceId(String contract, String group, String version) {
        return StableIds.fnv1a32(contract + '|' + group + '|' + version);
    }

    private static String clientClass(RustDubboProcessor.ReferenceContract reference) {
        return "Client_" + Integer.toUnsignedString(
                StableIds.fnv1a32(clientKey(reference)), 16);
    }

    private static String dispatcherClass(RustDubboProcessor.ServiceContract service) {
        return "Dispatcher_" + Integer.toUnsignedString(
                StableIds.fnv1a32(service.implementation()), 16);
    }

    private boolean isErasure(TypeMirror type, String qualifiedName) {
        TypeElement expected = elements.getTypeElement(qualifiedName);
        return expected != null && types.isSameType(types.erasure(type), types.erasure(expected.asType()));
    }

    private TypeMirror futureValueType(TypeMirror type, Element usage) throws IOException {
        if (!(type instanceof DeclaredType declared) || declared.getTypeArguments().size() != 1) {
            return fail(usage, "CompletableFuture Dubbo methods must declare one concrete result type: "
                    + type);
        }
        TypeMirror valueType = declared.getTypeArguments().get(0);
        if (valueType.getKind() == TypeKind.WILDCARD || valueType.getKind() == TypeKind.TYPEVAR) {
            return fail(usage, "CompletableFuture Dubbo result must be a concrete type: " + type);
        }
        return valueType;
    }

    private <T> T fail(Element element, String message) throws IOException {
        processing.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
        throw new IOException(message);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void line(StringBuilder source, String line) {
        source.append(line).append('\n');
    }

    private final class CodecRegistry {
        private final Map<String, CodecType> registered = new LinkedHashMap<>();

        void register(TypeMirror type, Element usage) throws IOException {
            String key = key(type);
            if (registered.containsKey(key)) {
                return;
            }
            CodecType codec = describe(type, usage);
            registered.put(key, codec);
            for (TypeMirror dependency : codec.dependencies()) {
                register(dependency, usage);
            }
        }

        String writeCall(TypeMirror type, String output, String value) {
            return methodName("write", type) + '(' + output + ", " + value + ')';
        }

        String readCall(TypeMirror type, String input) {
            return methodName("read", type) + '(' + input + ')';
        }

        void emit(StringBuilder source) throws IOException {
            for (CodecType codec : registered.values()) {
                codec.emit(source);
            }
        }

        private CodecType describe(TypeMirror type, Element usage) throws IOException {
            if (type.getKind().isPrimitive()) {
                return new PrimitiveCodec(type);
            }
            if (type.getKind() == TypeKind.ARRAY) {
                return new ArrayCodec((ArrayType) type, usage);
            }
            if (!(type instanceof DeclaredType)) {
                return fail(usage, "Unsupported Dubbo contract type: " + type);
            }
            DeclaredType declared = (DeclaredType) type;
            String erased = types.erasure(type).toString();
            if (isSimpleDeclared(erased)) {
                return new SimpleDeclaredCodec(declared, erased);
            }
            if (isAssignableErasure(type, "java.util.Set")) {
                return new SetCodec(declared, usage);
            }
            if (isAssignableErasure(type, "java.util.List")
                    || isAssignableErasure(type, "java.util.Collection")) {
                return new ListCodec(declared, usage);
            }
            if (isAssignableErasure(type, "java.util.Map")) {
                return new MapCodec(declared, usage);
            }
            TypeElement element = (TypeElement) declared.asElement();
            if (element.getKind() == ElementKind.ENUM) {
                return new EnumCodec(declared, element);
            }
            return new BeanCodec(declared, element, usage);
        }

        private boolean isAssignableErasure(TypeMirror type, String targetName) {
            TypeElement target = elements.getTypeElement(targetName);
            return target != null && types.isAssignable(types.erasure(type), types.erasure(target.asType()));
        }

        private boolean isSimpleDeclared(String erased) {
            return Set.of(
                    "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
                    "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Character",
                    "java.lang.Object", "java.lang.String", "java.math.BigDecimal", "java.util.Date",
                    "java.time.LocalDate", "java.time.LocalTime", "java.time.LocalDateTime"
            ).contains(erased);
        }

        private String methodName(String prefix, TypeMirror type) {
            return prefix + '_' + Integer.toUnsignedString(StableIds.fnv1a32(key(type)), 16);
        }

        private String key(TypeMirror type) {
            return type.toString();
        }

        private interface CodecType {
            List<TypeMirror> dependencies();
            void emit(StringBuilder source) throws IOException;
        }

        private final class PrimitiveCodec implements CodecType {
            private final TypeMirror type;

            private PrimitiveCodec(TypeMirror type) { this.type = type; }
            @Override public List<TypeMirror> dependencies() { return List.of(); }

            @Override
            public void emit(StringBuilder source) {
                String write = switch (type.getKind()) {
                    case BOOLEAN -> "out.writeBoolean(value)";
                    case BYTE, SHORT, INT, CHAR -> "out.writeInt(value)";
                    case LONG -> "out.writeLong(value)";
                    case FLOAT, DOUBLE -> "out.writeDouble(value)";
                    default -> throw new IllegalStateException("Unexpected primitive " + type);
                };
                String read = switch (type.getKind()) {
                    case BOOLEAN -> "in.readBoolean()";
                    case BYTE -> "(byte) in.readInt()";
                    case SHORT -> "(short) in.readInt()";
                    case INT -> "in.readInt()";
                    case CHAR -> "(char) in.readInt()";
                    case LONG -> "in.readLong()";
                    case FLOAT -> "(float) in.readDouble()";
                    case DOUBLE -> "in.readDouble()";
                    default -> throw new IllegalStateException("Unexpected primitive " + type);
                };
                line(source, "  private static void " + methodName("write", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Output out, " + type + " value) {");
                line(source, "    " + write + ';');
                line(source, "  }");
                line(source, "  private static " + type + ' ' + methodName("read", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Input in) {");
                line(source, "    return " + read + ';');
                line(source, "  }");
            }
        }

        private final class SimpleDeclaredCodec implements CodecType {
            private final DeclaredType type;
            private final String erased;

            private SimpleDeclaredCodec(DeclaredType type, String erased) {
                this.type = type;
                this.erased = erased;
            }
            @Override public List<TypeMirror> dependencies() { return List.of(); }

            @Override
            public void emit(StringBuilder source) {
                String write;
                String read;
                switch (erased) {
                    case "java.lang.Boolean" -> { write = "out.writeBoolean(value)"; read = "in.readBoolean()"; }
                    case "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Character" -> {
                        write = "out.writeInt(value)";
                        read = erased.equals("java.lang.Byte") ? "(byte) in.readInt()"
                                : erased.equals("java.lang.Short") ? "(short) in.readInt()"
                                : erased.equals("java.lang.Character") ? "(char) in.readInt()"
                                : "in.readInt()";
                    }
                    case "java.lang.Long" -> { write = "out.writeLong(value)"; read = "in.readLong()"; }
                    case "java.lang.Float", "java.lang.Double" -> {
                        write = "out.writeDouble(value)";
                        read = erased.equals("java.lang.Float") ? "(float) in.readDouble()" : "in.readDouble()";
                    }
                    case "java.lang.Object" -> { write = "out.writeDynamic(value)"; read = "in.readDynamic()"; }
                    case "java.lang.String" -> { write = "out.writeString(value)"; read = "in.readString()"; }
                    case "java.math.BigDecimal" -> { write = "out.writeBigDecimal(value)"; read = "in.readBigDecimal()"; }
                    case "java.util.Date" -> { write = "out.writeDate(value)"; read = "in.readDate()"; }
                    case "java.time.LocalDate" -> { write = "out.writeLocalDate(value)"; read = "in.readLocalDate()"; }
                    case "java.time.LocalTime" -> { write = "out.writeLocalTime(value)"; read = "in.readLocalTime()"; }
                    case "java.time.LocalDateTime" -> { write = "out.writeLocalDateTime(value)"; read = "in.readLocalDateTime()"; }
                    default -> throw new IllegalStateException(erased);
                }
                line(source, "  private static void " + methodName("write", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Output out, " + type + " value) {");
                if (!erased.equals("java.lang.String") && !erased.equals("java.math.BigDecimal")
                        && !erased.equals("java.util.Date") && !erased.startsWith("java.time.")) {
                    line(source, "    if (value == null) { out.writeNull(); return; }");
                }
                line(source, "    " + write + ';');
                line(source, "  }");
                line(source, "  private static " + type + ' ' + methodName("read", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Input in) {");
                if (!erased.equals("java.lang.String") && !erased.equals("java.math.BigDecimal")
                        && !erased.equals("java.util.Date") && !erased.startsWith("java.time.")) {
                    line(source, "    if (in.readNull()) return null;");
                }
                line(source, "    return " + read + ';');
                line(source, "  }");
            }
        }

        private abstract class CollectionCodec implements CodecType {
            final DeclaredType type;
            final TypeMirror item;

            CollectionCodec(DeclaredType type, Element usage) throws IOException {
                this.type = type;
                if (type.getTypeArguments().size() != 1) {
                    fail(usage, "Dubbo collections must declare one concrete generic type: " + type);
                }
                item = type.getTypeArguments().get(0);
            }
            @Override public List<TypeMirror> dependencies() { return List.of(item); }
        }

        private final class ListCodec extends CollectionCodec {
            ListCodec(DeclaredType type, Element usage) throws IOException { super(type, usage); }
            @Override public void emit(StringBuilder source) {
                emitCollection(source, type, item, "java.util.ArrayList", false);
            }
        }

        private final class SetCodec extends CollectionCodec {
            SetCodec(DeclaredType type, Element usage) throws IOException { super(type, usage); }
            @Override public void emit(StringBuilder source) {
                emitCollection(source, type, item, "java.util.LinkedHashSet", true);
            }
        }

        private void emitCollection(StringBuilder source, DeclaredType type, TypeMirror item,
                                    String implementation, boolean set) {
            line(source, "  private static void " + methodName("write", type)
                    + "(com.reactor.rust.dubbo.runtime.Hessian2Output out, " + type + " value) {");
            line(source, "    if (value == null) { out.writeNull(); return; }");
            line(source, "    out.writeListStart(value.size());");
            line(source, "    for (" + item + " item : value) " + writeCall(item, "out", "item") + ';');
            line(source, "  }");
            line(source, "  private static " + type + ' ' + methodName("read", type)
                    + "(com.reactor.rust.dubbo.runtime.Hessian2Input in) {");
            line(source, "    if (in.readNull()) return null;");
            line(source, "    int size = in.readListStart();");
            line(source, "    " + type + " value = new " + implementation + "<>(Math.max(size, 0));");
            line(source, "    int index = 0;");
            line(source, "    while (in.hasMoreListEntries(size, index)) {");
            line(source, "      value.add(" + readCall(item, "in") + ");");
            line(source, "      index++;");
            line(source, "    }");
            line(source, "    in.readListEnd(size);");
            line(source, "    return value;");
            line(source, "  }");
        }

        private final class MapCodec implements CodecType {
            private final DeclaredType type;
            private final TypeMirror keyType;
            private final TypeMirror valueType;

            MapCodec(DeclaredType type, Element usage) throws IOException {
                this.type = type;
                if (type.getTypeArguments().size() != 2) {
                    fail(usage, "Dubbo maps must declare concrete key and value types: " + type);
                }
                keyType = type.getTypeArguments().get(0);
                valueType = type.getTypeArguments().get(1);
            }
            @Override public List<TypeMirror> dependencies() { return List.of(keyType, valueType); }
            @Override public void emit(StringBuilder source) {
                line(source, "  private static void " + methodName("write", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Output out, " + type + " value) {");
                line(source, "    if (value == null) { out.writeNull(); return; }");
                line(source, "    out.writeMapStart();");
                line(source, "    for (java.util.Map.Entry<" + keyType + ", " + valueType
                        + "> entry : value.entrySet()) {");
                line(source, "      " + writeCall(keyType, "out", "entry.getKey()") + ';');
                line(source, "      " + writeCall(valueType, "out", "entry.getValue()") + ';');
                line(source, "    }");
                line(source, "    out.writeMapEnd();");
                line(source, "  }");
                line(source, "  private static " + type + ' ' + methodName("read", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Input in) {");
                line(source, "    if (in.readNull()) return null;");
                line(source, "    in.readMapStart();");
                line(source, "    " + type + " value = new java.util.LinkedHashMap<>();");
                line(source, "    while (in.hasMoreMapEntries()) value.put(" + readCall(keyType, "in")
                        + ", " + readCall(valueType, "in") + ");");
                line(source, "    in.readMapEnd();");
                line(source, "    return value;");
                line(source, "  }");
            }
        }

        private final class ArrayCodec implements CodecType {
            private final ArrayType type;
            private final TypeMirror component;
            private final boolean bytes;

            ArrayCodec(ArrayType type, Element usage) throws IOException {
                this.type = type;
                component = type.getComponentType();
                bytes = component.getKind() == TypeKind.BYTE;
            }
            @Override public List<TypeMirror> dependencies() {
                return bytes || component.toString().equals("java.lang.Object")
                        ? List.of() : List.of(component);
            }
            @Override public void emit(StringBuilder source) {
                line(source, "  private static void " + methodName("write", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Output out, " + type + " value) {");
                if (bytes) {
                    line(source, "    out.writeBinary(value);");
                } else if (component.toString().equals("java.lang.Object")) {
                    line(source, "    if (value == null) { out.writeNull(); return; }");
                    line(source, "    out.writeListStart(value.length);");
                    line(source, "    for (Object item : value) out.writeDynamic(item);");
                } else {
                    line(source, "    if (value == null) { out.writeNull(); return; }");
                    line(source, "    out.writeListStart(value.length);");
                    line(source, "    for (" + component + " item : value) "
                            + writeCall(component, "out", "item") + ';');
                }
                line(source, "  }");
                line(source, "  private static " + type + ' ' + methodName("read", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Input in) {");
                if (bytes) {
                    line(source, "    return in.readBinary();");
                } else if (component.toString().equals("java.lang.Object")) {
                    line(source, "    if (in.readNull()) return null;");
                    line(source, "    int size = in.readListStart();");
                    line(source, "    if (size >= 0) {");
                    line(source, "      Object[] value = new Object[size];");
                    line(source, "      for (int index = 0; index < size; index++) "
                            + "value[index] = in.readDynamic();");
                    line(source, "      return value;");
                    line(source, "    }");
                    line(source, "    java.util.ArrayList<Object> values = new java.util.ArrayList<>();");
                    line(source, "    int index = 0;");
                    line(source, "    while (in.hasMoreListEntries(size, index)) {");
                    line(source, "      values.add(in.readDynamic());");
                    line(source, "      index++;");
                    line(source, "    }");
                    line(source, "    in.readListEnd(size);");
                    line(source, "    return values.toArray();");
                } else {
                    line(source, "    if (in.readNull()) return null;");
                    line(source, "    int size = in.readListStart();");
                    line(source, "    if (size < 0) throw new com.reactor.rust.dubbo.runtime."
                            + "DubboCodecException(\"Variable-length typed arrays are not supported\");");
                    line(source, "    " + type + " value = new " + component + "[size];");
                    line(source, "    for (int index = 0; index < size; index++) value[index] = "
                            + readCall(component, "in") + ';');
                    line(source, "    return value;");
                }
                line(source, "  }");
            }
        }

        private final class EnumCodec implements CodecType {
            private final DeclaredType type;
            private final TypeElement element;
            private final List<String> constants;

            EnumCodec(DeclaredType type, TypeElement element) {
                this.type = type;
                this.element = element;
                constants = element.getEnclosedElements().stream()
                        .filter(current -> current.getKind() == ElementKind.ENUM_CONSTANT)
                        .map(current -> current.getSimpleName().toString()).toList();
            }
            @Override public List<TypeMirror> dependencies() { return List.of(); }
            @Override public void emit(StringBuilder source) {
                String fields = fieldsConstant(type);
                line(source, "  private static final String[] " + fields + " = {\"name\"};");
                line(source, "  private static void " + methodName("write", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Output out, " + type + " value) {");
                line(source, "    if (value == null) { out.writeNull(); return; }");
                line(source, "    out.writeObjectStart(\"" + binaryName(element) + "\", " + fields + ");");
                line(source, "    out.writeString(value.name());");
                line(source, "  }");
                line(source, "  private static " + type + ' ' + methodName("read", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Input in) {");
                line(source, "    if (in.readNull()) return null;");
                line(source, "    in.expectObject(\"" + binaryName(element) + "\", " + fields + ");");
                line(source, "    return switch (in.readString()) {");
                for (String constant : constants) {
                    line(source, "      case \"" + constant + "\" -> " + type + '.' + constant + ';');
                }
                line(source, "      default -> throw new com.reactor.rust.dubbo.runtime.DubboCodecException("
                        + "\"Unknown enum value for " + escape(type.toString()) + "\");");
                line(source, "    };");
                line(source, "  }");
            }
        }

        private final class BeanCodec implements CodecType {
            private final DeclaredType type;
            private final TypeElement element;
            private final List<BeanProperty> properties;
            private final boolean record;
            private final boolean builder;

            BeanCodec(DeclaredType type, TypeElement element, Element usage) throws IOException {
                this.type = type;
                this.element = element;
                record = element.getKind() == ElementKind.RECORD;
                properties = record ? recordProperties(element) : beanProperties(type, element, usage);
                if (properties.isEmpty()) {
                    fail(usage, "Dubbo DTO has no serializable properties: " + type);
                }
                builder = !record && hasCompatibleBuilder(type, element, properties);
                if (!record && !builder) {
                    requireWritable(element, properties, usage);
                    requireConstructible(element, usage);
                }
            }
            @Override public List<TypeMirror> dependencies() {
                return properties.stream().map(BeanProperty::type).toList();
            }
            @Override public void emit(StringBuilder source) {
                String fields = fieldsConstant(type);
                line(source, "  private static final String[] " + fields + " = {"
                        + properties.stream().map(property -> "\"" + escape(property.name()) + "\"")
                        .reduce((left, right) -> left + ", " + right).orElse("") + "};");
                line(source, "  private static void " + methodName("write", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Output out, " + type + " value) {");
                line(source, "    if (value == null) { out.writeNull(); return; }");
                line(source, "    out.writeObjectStart(\"" + binaryName(element) + "\", " + fields + ");");
                for (BeanProperty property : properties) {
                    line(source, "    " + writeCall(property.type(), "out", property.read("value")) + ';');
                }
                line(source, "  }");
                emitBeanReader(source, fields);
            }

            private void emitBeanReader(StringBuilder source, String fields) {
                line(source, "  private static " + type + ' ' + methodName("read", type)
                        + "(com.reactor.rust.dubbo.runtime.Hessian2Input in) {");
                line(source, "    if (in.readNull()) return null;");
                line(source, "    String[] incomingFields = in.readObjectFields(\""
                        + binaryName(element) + "\");");
                for (int index = 0; index < properties.size(); index++) {
                    BeanProperty property = properties.get(index);
                    line(source, "    " + property.type() + " field" + index + " = "
                            + defaultValue(property.type()) + ';');
                }
                line(source, "    for (String field : incomingFields) {");
                line(source, "      switch (field) {");
                for (int index = 0; index < properties.size(); index++) {
                    BeanProperty property = properties.get(index);
                    line(source, "        case \"" + escape(property.name()) + "\" -> field" + index
                            + " = " + readCall(property.type(), "in") + ';');
                }
                line(source, "        default -> in.skipValue();");
                line(source, "      }");
                line(source, "    }");
                if (record) {
                    line(source, "    return new " + type + '(' + fieldNames(properties.size()) + ");");
                } else if (builder) {
                    StringBuilder expression = new StringBuilder(type.toString()).append(".builder()");
                    for (int index = 0; index < properties.size(); index++) {
                        expression.append('.').append(properties.get(index).name())
                                .append("(field").append(index).append(')');
                    }
                    line(source, "    return " + expression + ".build();");
                } else {
                    line(source, "    " + type + " value = new " + type + "();");
                    for (int index = 0; index < properties.size(); index++) {
                        line(source, "    " + properties.get(index).write("value", "field" + index));
                    }
                    line(source, "    return value;");
                }
                line(source, "  }");
            }
        }

        private boolean hasCompatibleBuilder(DeclaredType ownerType, TypeElement element,
                                             List<BeanProperty> properties) {
            ExecutableElement factory = null;
            for (Element member : elements.getAllMembers(element)) {
                if (member.getKind() == ElementKind.METHOD
                        && member.getSimpleName().contentEquals("builder")
                        && member.getModifiers().contains(Modifier.PUBLIC)
                        && member.getModifiers().contains(Modifier.STATIC)
                        && ((ExecutableElement) member).getParameters().isEmpty()) {
                    factory = (ExecutableElement) member;
                    break;
                }
            }
            if (factory == null || !(factory.getReturnType() instanceof DeclaredType builderType)
                    || !(builderType.asElement() instanceof TypeElement builderElement)) {
                return false;
            }
            ExecutableElement build = findMethod(builderElement, "build", 0);
            if (build == null || !types.isAssignable(types.erasure(build.getReturnType()),
                    types.erasure(ownerType))) {
                return false;
            }
            for (BeanProperty property : properties) {
                ExecutableElement writer = findMethod(builderElement, property.name(), 1);
                if (writer == null || !types.isAssignable(property.type(),
                        writer.getParameters().get(0).asType())) {
                    return false;
                }
            }
            return true;
        }

        private List<BeanProperty> recordProperties(TypeElement element) {
            List<BeanProperty> result = new ArrayList<>();
            for (RecordComponentElement component : element.getRecordComponents()) {
                String name = component.getSimpleName().toString();
                result.add(new BeanProperty(name, component.asType(), name + "()", null));
            }
            return List.copyOf(result);
        }

        private List<BeanProperty> beanProperties(DeclaredType ownerType, TypeElement element,
                                                  Element usage) throws IOException {
            Map<String, VariableElement> fields = new LinkedHashMap<>();
            for (Element member : elements.getAllMembers(element)) {
                if (member.getKind() != ElementKind.FIELD) continue;
                VariableElement field = (VariableElement) member;
                if (field.getModifiers().contains(Modifier.STATIC)
                        || field.getModifiers().contains(Modifier.TRANSIENT)) continue;
                fields.putIfAbsent(field.getSimpleName().toString(), field);
            }
            List<BeanProperty> result = new ArrayList<>();
            fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                String name = entry.getKey();
                VariableElement field = entry.getValue();
                String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                ExecutableElement getter = findMethod(element, "get" + capitalized, 0);
                if (getter == null && field.asType().getKind() == TypeKind.BOOLEAN) {
                    if (isBooleanPrefixed(name)) {
                        getter = findMethod(element, name, 0);
                    }
                }
                if (getter == null && field.asType().getKind() == TypeKind.BOOLEAN) {
                    getter = findMethod(element, "is" + capitalized, 0);
                }
                ExecutableElement setter = findMethod(element, "set" + capitalized, 1);
                if (setter == null && field.asType().getKind() == TypeKind.BOOLEAN
                        && isBooleanPrefixed(name)) {
                    setter = findMethod(element, "set" + name.substring(2), 1);
                }
                String read = getter != null ? getter.getSimpleName() + "()"
                        : field.getModifiers().contains(Modifier.PUBLIC) ? name : null;
                String write = setter != null ? setter.getSimpleName().toString()
                        : field.getModifiers().contains(Modifier.PUBLIC)
                                && !field.getModifiers().contains(Modifier.FINAL) ? name : null;
                if (read != null) {
                    TypeMirror resolved = types.asMemberOf(ownerType, field);
                    result.add(new BeanProperty(name, resolved, read, write));
                }
            });
            if (result.size() != fields.size()) {
                Set<String> supported = result.stream().map(BeanProperty::name)
                        .collect(java.util.stream.Collectors.toSet());
                String missing = fields.keySet().stream().filter(name -> !supported.contains(name))
                        .sorted().reduce((left, right) -> left + ", " + right).orElse("");
                fail(usage, "Dubbo DTO requires public getters for generated codecs: "
                        + element.getQualifiedName() + " missing " + missing);
            }
            return List.copyOf(result);
        }

        private boolean isBooleanPrefixed(String name) {
            return name.length() > 2 && name.startsWith("is")
                    && Character.isUpperCase(name.charAt(2));
        }

        private void requireWritable(TypeElement element, List<BeanProperty> properties,
                                     Element usage) throws IOException {
            String missing = properties.stream().filter(property -> property.writer() == null)
                    .map(BeanProperty::name).sorted()
                    .reduce((left, right) -> left + ", " + right).orElse("");
            if (!missing.isEmpty()) {
                fail(usage, "Dubbo DTO requires public setters or a compatible public builder: "
                        + element.getQualifiedName() + " missing " + missing);
            }
        }

        private ExecutableElement findMethod(TypeElement element, String name, int parameterCount) {
            for (Element member : elements.getAllMembers(element)) {
                if (member.getKind() == ElementKind.METHOD
                        && member.getSimpleName().contentEquals(name)
                        && member.getModifiers().contains(Modifier.PUBLIC)
                        && ((ExecutableElement) member).getParameters().size() == parameterCount) {
                    return (ExecutableElement) member;
                }
            }
            return null;
        }

        private void requireConstructible(TypeElement element, Element usage) throws IOException {
            List<ExecutableElement> constructors = element.getEnclosedElements().stream()
                    .filter(current -> current.getKind() == ElementKind.CONSTRUCTOR)
                    .map(current -> (ExecutableElement) current).toList();
            boolean implicit = constructors.isEmpty() && element.getModifiers().contains(Modifier.PUBLIC);
            boolean explicit = constructors.stream().anyMatch(constructor -> constructor.getParameters().isEmpty()
                    && constructor.getModifiers().contains(Modifier.PUBLIC));
            if (!implicit && !explicit) {
                fail(usage, "Dubbo DTO requires a public no-arg constructor: "
                        + element.getQualifiedName());
            }
        }

        private String fieldsConstant(TypeMirror type) {
            return "FIELDS_" + Integer.toUnsignedString(StableIds.fnv1a32(key(type)), 16);
        }

        private String binaryName(TypeElement type) {
            return elements.getBinaryName(type).toString();
        }

        private String defaultValue(TypeMirror type) {
            return switch (type.getKind()) {
                case BOOLEAN -> "false";
                case BYTE, SHORT, INT, LONG, CHAR -> "0";
                case FLOAT -> "0.0f";
                case DOUBLE -> "0.0d";
                default -> "null";
            };
        }

        private String fieldNames(int count) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < count; index++) {
                if (index > 0) result.append(", ");
                result.append("field").append(index);
            }
            return result.toString();
        }

        private record BeanProperty(String name, TypeMirror type, String reader, String writer) {
            String read(String target) {
                return target + '.' + reader;
            }

            String write(String target, String value) {
                return writer.endsWith("(") ? target + '.' + writer + value + ");"
                        : writer.startsWith("set") ? target + '.' + writer + '(' + value + ");"
                        : target + '.' + writer + " = " + value + ';';
            }
        }
    }
}
