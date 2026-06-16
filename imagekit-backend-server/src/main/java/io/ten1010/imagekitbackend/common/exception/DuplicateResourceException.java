package io.ten1010.imagekitbackend.common.exception;

/**
 * 이미 존재하는 리소스를 중복 생성/변경하려 할 때 던진다. (HTTP 409 Conflict 로 매핑)
 * 예: 같은 (project, username) 안에서 동일한 이름의 Dockerfile 생성.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

}
