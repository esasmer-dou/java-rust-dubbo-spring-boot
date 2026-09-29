package com.reactor.rust.dubbo.runtime;

import java.util.Iterator;
import java.util.List;

/**
 * A list that can feed the generated Dubbo encoder without first materializing
 * every element in the provider heap.
 *
 * <p>The regular {@link List} methods must retain normal Java list semantics.
 * {@link #streamingIterator()} is a single-pass provider transport contract and
 * is called only by generated provider code.</p>
 */
public interface DubboStreamingList<E> extends List<E>, AutoCloseable {

    Iterator<E> streamingIterator();

    @Override
    void close();
}
