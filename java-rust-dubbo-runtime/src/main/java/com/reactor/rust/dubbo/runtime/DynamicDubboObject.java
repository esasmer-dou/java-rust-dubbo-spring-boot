package com.reactor.rust.dubbo.runtime;

import java.util.Map;

/** Explicit representation for a Hessian object whose Java schema is not part of the contract. */
public record DynamicDubboObject(String typeName, Map<String, Object> fields) {
}
