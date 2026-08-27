package de.venomenon.cscxtool.shared;

public class ApiBadRequestException extends RuntimeException {

    private final String code;

    public ApiBadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
