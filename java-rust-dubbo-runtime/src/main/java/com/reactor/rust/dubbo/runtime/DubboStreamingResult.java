package com.reactor.rust.dubbo.runtime;

import java.util.Iterator;
import java.util.List;

/**
 * A native-backed list result that can be consumed without retaining every decoded item.
 *
 * <p>Regular {@link List} operations preserve normal list behavior by materializing decoded
 * values on demand. {@link #streamingIterator()} is the low-allocation, single-pass path. Once
 * that iterator is requested, regular list operations are no longer available. Callers must
 * close the result when iteration does not reach the end, for example after a client disconnect.</p>
 */
public interface DubboStreamingResult<E> extends List<E>, AutoCloseable {

    /**
     * Opens the one-pass consumer iterator. Requesting it again after streaming
     * starts throws an {@link IllegalStateException} that identifies the one-pass rule.
     * A result already materialized through regular list operations remains repeatable.
     */
    Iterator<E> streamingIterator();

    @Override
    void close();
}
