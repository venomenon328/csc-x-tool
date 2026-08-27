package de.venomenon.cscxtool.shared;

public class ApiConflictException extends RuntimeException {

    private final String code;

    public ApiConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
