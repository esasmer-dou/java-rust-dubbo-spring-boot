package com.reactor.rust.dubbo.runtime;

import java.io.IOException;

/** Compile-time generated, statically bound value transcoder. */
@FunctionalInterface
public interface Hessian2JsonWriter {
    void write(Hessian2Input input, Hessian2JsonTranscoder output) throws IOException;
}
