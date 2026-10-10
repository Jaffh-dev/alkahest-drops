package com.jaffh.alkahestcore.dropscore.server.mobdrops;

/**
 * Signals that a well-formed id names nothing registered, usually because an optional mod is not installed.
 * Logged as a warning instead of an error.
 */
public class UnknownIdException extends InvalidDropDataException {

    /**
     * Creates the exception with an English message for the log.
     *
     * @param message Problem description, naming the field and the unknown id.
     */
    public UnknownIdException(String message) {
        super(message);
    }
}
