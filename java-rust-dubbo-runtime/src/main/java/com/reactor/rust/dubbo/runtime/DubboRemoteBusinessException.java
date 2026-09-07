package com.reactor.rust.dubbo.runtime;

import java.util.Map;

/** A business exception returned by a remote Dubbo provider. */
public final class DubboRemoteBusinessException extends DubboNativeException {
    private static final int MAX_CAUSE_DEPTH = 8;
    private static final String UNKNOWN_REMOTE_TYPE = "java.lang.Throwable";

    private final String remoteType;
    private final String remoteMessage;

    private DubboRemoteBusinessException(RemoteFailure failure) {
        super(displayMessage(failure), createCause(failure.cause()));
        remoteType = failure.type();
        remoteMessage = failure.message();
    }

    /** Returns the exception class name reported by the provider. */
    public String remoteType() {
        return remoteType;
    }

    /** Returns the unmodified exception message reported by the provider. */
    public String remoteMessage() {
        return remoteMessage;
    }

    static DubboRemoteBusinessException fromPayload(Object payload) {
        RemoteFailure failure = decode(payload, 0);
        if (failure == null) {
            failure = new RemoteFailure(
                    UNKNOWN_REMOTE_TYPE, "Remote provider returned an empty business exception", null);
        }
        return new DubboRemoteBusinessException(failure);
    }

    private static Throwable createCause(RemoteFailure failure) {
        return failure == null ? null : new DubboRemoteBusinessException(failure);
    }

    private static RemoteFailure decode(Object value, int depth) {
        if (depth >= MAX_CAUSE_DEPTH || value == null) {
            return null;
        }
        if (value instanceof DynamicDubboObject object) {
            Map<String, Object> fields = object.fields();
            String type = firstString(fields, "remoteType", "exceptionClass", "className");
            if (type == null || type.isBlank()) {
                type = object.typeName();
            }
            String message = firstString(fields, "detailMessage", "message", "exceptionMessage");
            RemoteFailure cause = decode(fields.get("cause"), depth + 1);
            return new RemoteFailure(normalizeType(type), message, cause);
        }
        if (value instanceof Map<?, ?> fields) {
            String type = firstString(fields, "remoteType", "exceptionClass", "className", "type");
            String message = firstString(fields, "detailMessage", "message", "exceptionMessage");
            RemoteFailure cause = decode(fields.get("cause"), depth + 1);
            return new RemoteFailure(normalizeType(type), message, cause);
        }
        if (value instanceof Throwable throwable) {
            return new RemoteFailure(throwable.getClass().getName(), throwable.getMessage(),
                    decode(throwable.getCause(), depth + 1));
        }
        if (value instanceof String message) {
            return new RemoteFailure(UNKNOWN_REMOTE_TYPE, message, null);
        }
        return new RemoteFailure(UNKNOWN_REMOTE_TYPE, "Unrecognized remote business exception payload", null);
    }

    private static String firstString(Map<?, ?> fields, String... names) {
        for (String name : names) {
            Object value = fields.get(name);
            if (value instanceof String text) {
                return text;
            }
        }
        return null;
    }

    private static String normalizeType(String type) {
        return type == null || type.isBlank() ? UNKNOWN_REMOTE_TYPE : type;
    }

    private static String displayMessage(RemoteFailure failure) {
        return failure.message() == null || failure.message().isBlank()
                ? "Remote business exception: " + failure.type()
                : failure.message();
    }

    private record RemoteFailure(String type, String message, RemoteFailure cause) {
    }
}
