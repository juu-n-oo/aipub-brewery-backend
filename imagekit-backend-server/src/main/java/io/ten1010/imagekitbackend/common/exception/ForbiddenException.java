package io.ten1010.imagekitbackend.common.exception;

/**
 * 인증은 되었으나 해당 작업을 수행할 권한이 없을 때 던진다. {@code GlobalExceptionHandler} 가 403 으로 매핑한다.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

}
