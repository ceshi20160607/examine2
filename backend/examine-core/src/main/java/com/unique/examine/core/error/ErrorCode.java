package com.unique.examine.core.error;

/**
 * Typed error code contract.
 */
public interface ErrorCode {

    /**
     * Domain-prefixed error code.
     *
     * @return code
     */
    String code();

    /**
     * User-readable default message.
     *
     * @return message
     */
    String message();
}

