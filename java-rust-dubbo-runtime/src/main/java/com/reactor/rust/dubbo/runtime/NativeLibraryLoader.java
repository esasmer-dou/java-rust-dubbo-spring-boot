package com.reactor.rust.dubbo.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class NativeLibraryLoader {
    private static final String EXPLICIT_PATH = "reactor.dubbo.native.path";

    private NativeLibraryLoader() {
    }

    static void load() {
        String explicit = System.getProperty(EXPLICIT_PATH);
        if (explicit != null && !explicit.isBlank()) {
            System.load(Path.of(explicit).toAbsolutePath().normalize().toString());
            return;
        }

        String resource = resourceName();
        try (InputStream input = NativeLibraryLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new DubboNativeException(
                        "Native Dubbo library is missing: " + resource
                                + ". Set -D" + EXPLICIT_PATH + "=/absolute/path/to/library");
            }
            byte[] binary = input.readAllBytes();
            String hash = sha256(binary).substring(0, 16);
            String fileName = resource.substring(resource.lastIndexOf('/') + 1);
            Path target = Path.of(System.getProperty("user.home"), ".java-rust-dubbo", "native", hash, fileName);
            Files.createDirectories(target.getParent());
            if (!Files.exists(target) || Files.size(target) != binary.length) {
                Path staging = target.resolveSibling(fileName + ".staging");
                Files.write(staging, binary);
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            System.load(target.toAbsolutePath().toString());
        } catch (IOException exception) {
            throw new DubboNativeException("Failed to install the native Dubbo library", exception);
        }
    }

    private static String resourceName() {
        return resourceName(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static String resourceName(String operatingSystem, String architecture) {
        String os = operatingSystem.toLowerCase(Locale.ROOT);
        String arch = architecture.toLowerCase(Locale.ROOT);
        if ((os.contains("mac") || os.contains("darwin")) && isArm64(arch)) {
            return "/native/librust_dubbo-macos-aarch64.dylib";
        }
        if (os.contains("win") && isX64(arch)) {
            return "/native/rust_dubbo-windows-x64.dll";
        }
        if (os.contains("linux") && isX64(arch)) {
            return "/native/librust_dubbo-linux-x64.so";
        }
        throw new DubboNativeException(
                "Unsupported native Dubbo platform: os=" + operatingSystem + ", arch=" + architecture
                        + ". Supported platforms: Windows x64, Linux x64, macOS ARM64");
    }

    private static boolean isX64(String architecture) {
        return architecture.equals("amd64") || architecture.equals("x86_64") || architecture.equals("x64");
    }

    private static boolean isArm64(String architecture) {
        return architecture.equals("aarch64") || architecture.equals("arm64");
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
