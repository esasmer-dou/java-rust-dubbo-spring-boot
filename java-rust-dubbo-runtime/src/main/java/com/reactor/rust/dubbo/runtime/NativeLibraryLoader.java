package com.reactor.rust.dubbo.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!arch.contains("64") && !arch.equals("amd64") && !arch.equals("x86_64")) {
            throw new DubboNativeException("Only x64 native Dubbo binaries are supported: " + arch);
        }
        if (os.contains("win")) {
            return "/native/rust_dubbo-windows-x64.dll";
        }
        if (os.contains("linux")) {
            return "/native/librust_dubbo-linux-x64.so";
        }
        throw new DubboNativeException("Unsupported operating system: " + os);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
