package com.jaffh.alkahestcore.dropscore.server.mobdrops;

/**
 * Signals that a drop or a file holds invalid data and must be skipped.
 */
public class InvalidDropDataException extends Exception {

    /**
     * Creates the exception with an English message for the log.
     *
     * @param message Problem description, naming the bad field and value.
     */
    public InvalidDropDataException(String message) {
        super(message);
    }
}
