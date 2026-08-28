package com.reactor.rust.dubbo.enhancer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "enhance", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public final class EnhanceMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    private java.io.File classesDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        Path root = classesDirectory.toPath();
        if (!Files.isDirectory(root)) {
            return;
        }
        int enhanced = 0;
        try (var paths = Files.walk(root)) {
            List<Path> classes = paths.filter(path -> path.toString().endsWith(".class")).toList();
            for (Path path : classes) {
                byte[] original = Files.readAllBytes(path);
                byte[] transformed = ReferenceFieldEnhancer.enhance(original);
                if (transformed != original) {
                    Path staging = path.resolveSibling(path.getFileName() + ".staging");
                    Files.write(staging, transformed);
                    Files.move(staging, path, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    enhanced++;
                }
            }
        } catch (IOException exception) {
            throw new MojoExecutionException("Failed to enhance @DubboReference fields", exception);
        }
        getLog().info("Reflection-free Dubbo injection enhanced " + enhanced + " class(es)");
    }
}
