package io.ten1010.dockerizerbackend.common.exception;

import io.kubernetes.client.openapi.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * DB 유니크 제약 위반 안전망. 서비스의 사전 중복 검사를 통과한 동시성 충돌(race) 등에서
     * 발생할 수 있으므로 명시적으로 409 로 매핑해 500 누출을 막는다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "이미 같은 이름의 Dockerfile 이 있습니다.");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ForbiddenInstructionException.class)
    public ProblemDetail handleForbiddenInstruction(ForbiddenInstructionException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        detail.setProperty("forbiddenInstructions", e.getForbiddenInstructions());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 업스트림(k8s API server, AIPub proxy 등) 호출 실패. 원문(k8s responseBody 등)은 서버 로그에만 남기고
     * 클라이언트에는 일반 메시지로 502 를 반환한다(내부 정보 노출 방지, SRV-4).
     */
    @ExceptionHandler(UpstreamServiceException.class)
    public ProblemDetail handleUpstream(UpstreamServiceException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ApiException apiException) {
            log.error("{} (k8s code={}, body={})",
                    e.getMessage(), apiException.getCode(), apiException.getResponseBody(), e);
        } else {
            log.error("{}", e.getMessage(), e);
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "An upstream service is temporarily unavailable. Please try again.");
    }

    /**
     * 매핑되지 않은 모든 예외의 안전망. 스택트레이스는 로깅하되 클라이언트에는 메시지를 노출하지 않는다.
     * (이전엔 fallback 이 없어 raw RuntimeException 메시지가 그대로 500 body 로 누출됐다, SRV-4)
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
    }

}
