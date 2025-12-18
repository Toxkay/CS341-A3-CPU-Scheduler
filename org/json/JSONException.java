package org.json;

/**
 * Minimal runtime exception used by the lightweight JSON parser in this project.
 */
public class JSONException extends RuntimeException {
    public JSONException(String message) {
        super(message);
    }

    public JSONException(String message, Throwable cause) {
        super(message, cause);
    }
}
