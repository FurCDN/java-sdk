package us.furcdn.api;

import lombok.Getter;

@Getter
public class FurCdnException extends RuntimeException {
    private final int statusCode;

    public FurCdnException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
