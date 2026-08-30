package com.reactor.rust.dubbo.codegen;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

public final class RustDubboProcessor extends AbstractProcessor {
    static final String APACHE_COMPATIBILITY_OPTION = "reactor.dubbo.apacheCompatibility";
    private static final String REFERENCE = "com.reactor.rust.dubbo.annotation.DubboReference";
    private static final String SERVICE = "com.reactor.rust.dubbo.annotation.DubboService";
    private static final String ENABLE = "com.reactor.rust.dubbo.annotation.EnableDubbo";
    private static final String APACHE_REFERENCE = "org.apache.dubbo.config.annotation.DubboReference";
    private static final String APACHE_SERVICE = "org.apache.dubbo.config.annotation.DubboService";
    private static final String APACHE_ENABLE =
            "org.apache.dubbo.config.spring.context.annotation.EnableDubbo";

    private final Map<String, ReferenceContract> references = new LinkedHashMap<>();
    private final Map<String, ServiceContract> services = new LinkedHashMap<>();
    private boolean written;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(REFERENCE, SERVICE, ENABLE, APACHE_REFERENCE, APACHE_SERVICE, APACHE_ENABLE);
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(APACHE_COMPATIBILITY_OPTION);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        collectAnnotated(roundEnvironment, REFERENCE, SERVICE);
        if (apacheCompatibilityEnabled()) {
            collectAnnotated(roundEnvironment, APACHE_REFERENCE, APACHE_SERVICE);
        }
        if (!roundEnvironment.processingOver() && !written
                && (!references.isEmpty() || !services.isEmpty())) {
            written = true;
            writeIndex();
        }
        return false;
    }

    private void collectAnnotated(RoundEnvironment roundEnvironment,
                                  String referenceAnnotation, String serviceAnnotation) {
        TypeElement referenceType = processingEnv.getElementUtils().getTypeElement(referenceAnnotation);
        TypeElement serviceType = processingEnv.getElementUtils().getTypeElement(serviceAnnotation);
        if (referenceType != null) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(referenceType)) {
                collectReference(element, referenceAnnotation);
            }
        }
        if (serviceType != null) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(serviceType)) {
                collectService(element, serviceAnnotation);
            }
        }
    }

    private boolean apacheCompatibilityEnabled() {
        return Boolean.parseBoolean(
                processingEnv.getOptions().getOrDefault(APACHE_COMPATIBILITY_OPTION, "false"));
    }

    private void collectReference(Element element, String annotationName) {
        if (element.getKind() != ElementKind.FIELD) {
            error(element, "@DubboReference is supported on fields only in the reflection-free runtime");
            return;
        }
        VariableElement field = (VariableElement) element;
        if (field.getModifiers().contains(Modifier.STATIC) || field.getModifiers().contains(Modifier.FINAL)) {
            error(field, "@DubboReference field must be an assignable instance field");
            return;
        }
        if (!(field.asType() instanceof DeclaredType declared)
                || declared.asElement().getKind() != ElementKind.INTERFACE) {
            error(field, "@DubboReference field type must be an interface");
            return;
        }
        TypeElement owner = (TypeElement) field.getEnclosingElement();
        TypeElement contract = (TypeElement) declared.asElement();
        String ownerName = processingEnv.getElementUtils().getBinaryName(owner).toString();
        String contractName = processingEnv.getElementUtils().getBinaryName(contract).toString();
        AnnotationValues values = AnnotationValues.of(field, annotationName);
        if (!values.rejectUnsupported(field, Set.of(
                "interfaceClass", "interfaceName", "version", "group", "check"), processingEnv)) {
            return;
        }
        if (!validateExplicitContract(field, contract, values)) {
            return;
        }
        String key = ownerName + '#' + field.getSimpleName();
        if (references.containsKey(key)) {
            error(field, "Dubbo reference must use exactly one canonical or Apache-compat annotation");
            return;
        }
        references.put(key, new ReferenceContract(ownerName, field.getSimpleName().toString(), contractName,
                values.string("group"), values.string("version"), values.bool("check", true),
                contract, methods(contract)));
    }

    private void collectService(Element element, String annotationName) {
        if (element.getKind() != ElementKind.CLASS) {
            error(element, "@DubboService is supported on implementation classes only");
            return;
        }
        TypeElement implementation = (TypeElement) element;
        AnnotationValues values = AnnotationValues.of(implementation, annotationName);
        if (!values.rejectUnsupported(implementation, Set.of(
                "interfaceClass", "interfaceName", "version", "group", "export", "async",
                "executes", "executor"), processingEnv)) {
            return;
        }
        TypeElement contract = resolveServiceContract(implementation, values);
        if (contract == null) {
            return;
        }
        String implementationName = processingEnv.getElementUtils().getBinaryName(implementation).toString();
        String contractName = processingEnv.getElementUtils().getBinaryName(contract).toString();
        if (services.containsKey(implementationName)) {
            error(implementation,
                    "Dubbo service must use exactly one canonical or Apache-compat annotation");
            return;
        }
        services.put(implementationName, new ServiceContract(implementationName, contractName,
                values.string("group"), values.string("version"), values.string("executor"),
                values.bool("async", false), values.bool("export", true), values.integer("executes", -1),
                implementation, contract, methods(contract)));
    }

    private TypeElement resolveServiceContract(TypeElement implementation, AnnotationValues values) {
        TypeMirror explicitClass = values.type("interfaceClass");
        if (explicitClass != null && !explicitClass.toString().equals("void")) {
            Element explicit = processingEnv.getTypeUtils().asElement(explicitClass);
            if (!(explicit instanceof TypeElement type) || type.getKind() != ElementKind.INTERFACE) {
                error(implementation, "@DubboService interfaceClass must name an interface");
                return null;
            }
            return type;
        }
        String explicitName = values.string("interfaceName");
        if (!explicitName.isBlank()) {
            TypeElement explicit = processingEnv.getElementUtils().getTypeElement(explicitName);
            if (explicit == null) {
                error(implementation, "@DubboService interfaceName cannot be resolved: " + explicitName);
            }
            return explicit;
        }
        List<? extends TypeMirror> interfaces = implementation.getInterfaces();
        if (interfaces.size() != 1 || !(interfaces.get(0) instanceof DeclaredType declared)) {
            error(implementation,
                    "@DubboService implementation must implement exactly one interface or set interfaceName");
            return null;
        }
        return (TypeElement) declared.asElement();
    }

    private boolean validateExplicitContract(Element element, TypeElement contract,
                                             AnnotationValues values) {
        String interfaceName = values.string("interfaceName");
        String contractName = processingEnv.getElementUtils().getBinaryName(contract).toString();
        if (!interfaceName.isBlank() && !interfaceName.equals(contractName)) {
            error(element, "@DubboReference interfaceName must match the field interface " + contractName);
            return false;
        }
        TypeMirror interfaceClass = values.type("interfaceClass");
        if (interfaceClass != null && !interfaceClass.toString().equals("void")
                && !processingEnv.getTypeUtils().isSameType(
                        processingEnv.getTypeUtils().erasure(interfaceClass),
                        processingEnv.getTypeUtils().erasure(contract.asType()))) {
            error(element, "@DubboReference interfaceClass must match the field interface " + contractName);
            return false;
        }
        return true;
    }

    private List<MethodContract> methods(TypeElement contract) {
        Map<String, MethodContract> result = new LinkedHashMap<>();
        for (Element member : processingEnv.getElementUtils().getAllMembers(contract)) {
            if (member.getKind() != ElementKind.METHOD || member.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            if (method.getEnclosingElement().toString().equals("java.lang.Object")) {
                continue;
            }
            if (method.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            ExecutableType type = (ExecutableType) processingEnv.getTypeUtils().asMemberOf(
                    (DeclaredType) contract.asType(), method);
            try {
                String descriptor = JvmDescriptors.method(type, processingEnv.getElementUtils());
                String identity = processingEnv.getElementUtils().getBinaryName(contract) + "#"
                        + method.getSimpleName() + descriptor;
                String key = method.getSimpleName() + descriptor;
                result.putIfAbsent(key, new MethodContract(method.getSimpleName().toString(), descriptor,
                        StableIds.fnv1a32(identity), method, type));
            } catch (IllegalArgumentException exception) {
                error(method, exception.getMessage());
            }
        }
        List<MethodContract> sorted = new ArrayList<>(result.values());
        sorted.sort(Comparator.comparing(MethodContract::name).thenComparing(MethodContract::descriptor));
        return List.copyOf(sorted);
    }

    private void writeIndex() {
        if (references.isEmpty() && services.isEmpty()) {
            return;
        }
        try {
            Filer filer = processingEnv.getFiler();
            var resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/rust-dubbo/contracts.idx");
            try (Writer writer = resource.openWriter()) {
                writer.write("RUST_DUBBO_CONTRACTS_V1\n");
                references.values().stream().sorted(Comparator.comparing(ReferenceContract::owner))
                        .forEach(reference -> writeReference(writer, reference));
                services.values().stream().sorted(Comparator.comparing(ServiceContract::implementation))
                        .forEach(service -> writeService(writer, service));
            }
            new GeneratedSourceWriter(processingEnv, references.values(), services.values())
                    .write();
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write Rust Dubbo contract index: " + exception.getMessage());
        }
    }

    private void writeReference(Writer writer, ReferenceContract reference) {
        write(writer, "R|" + reference.owner() + '|' + reference.field() + '|' + reference.contract()
                + '|' + reference.group() + '|' + reference.version());
        writeMethods(writer, reference.contract(), reference.group(), reference.version(),
                reference.methods());
    }

    private void writeService(Writer writer, ServiceContract service) {
        write(writer, "S|" + service.implementation() + '|' + service.contract() + '|' + service.group()
                + '|' + service.version() + '|' + service.executor() + '|' + service.async());
        writeMethods(writer, service.contract(), service.group(), service.version(), service.methods());
    }

    private void writeMethods(Writer writer, String contract, String group, String version,
                              List<MethodContract> methods) {
        int serviceId = StableIds.fnv1a32(contract + '|' + group + '|' + version);
        for (MethodContract method : methods) {
            write(writer, "M|" + serviceId + '|' + method.id() + '|' + contract + '|'
                    + method.name() + '|' + method.descriptor());
        }
    }

    private void write(Writer writer, String line) {
        try {
            writer.write(line);
            writer.write('\n');
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    record MethodContract(String name, String descriptor, int id, ExecutableElement element,
                          ExecutableType type) {}
    record ReferenceContract(String owner, String field, String contract, String group,
                             String version, boolean check, TypeElement contractElement,
                             List<MethodContract> methods) {}
    record ServiceContract(String implementation, String contract, String group, String version,
                           String executor, boolean async, boolean export, int executes,
                           TypeElement implementationElement,
                           TypeElement contractElement, List<MethodContract> methods) {}

    private record AnnotationValues(Map<String, Object> values) {
        static AnnotationValues of(Element element, String annotationName) {
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                if (mirror.getAnnotationType().toString().equals(annotationName)) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    var elements = mirror.getElementValues();
                    for (var entry : elements.entrySet()) {
                        values.put(entry.getKey().getSimpleName().toString(), entry.getValue().getValue());
                    }
                    return new AnnotationValues(values);
                }
            }
            return new AnnotationValues(Map.of());
        }

        String string(String name) {
            Object value = values.get(name);
            return value == null ? "" : value.toString();
        }

        boolean bool(String name) {
            return bool(name, false);
        }

        boolean bool(String name, boolean defaultValue) {
            Object value = values.get(name);
            return value instanceof Boolean current ? current : defaultValue;
        }

        int integer(String name, int defaultValue) {
            Object value = values.get(name);
            return value instanceof Integer current ? current : defaultValue;
        }

        TypeMirror type(String name) {
            Object value = values.get(name);
            return value instanceof TypeMirror current ? current : null;
        }

        boolean rejectUnsupported(Element element, Set<String> supported,
                                  javax.annotation.processing.ProcessingEnvironment environment) {
            for (String name : values.keySet()) {
                if (!supported.contains(name)) {
                    environment.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@RustDubbo does not implement annotation attribute '" + name
                                    + "'; remove it or use an explicitly supported native option",
                            element);
                    return false;
                }
            }
            return true;
        }
    }
}
