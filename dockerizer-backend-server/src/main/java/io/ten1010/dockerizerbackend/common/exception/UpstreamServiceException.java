package io.ten1010.dockerizerbackend.common.exception;

/**
 * 업스트림 의존 서비스(k8s API server, AIPub proxy 등) 호출 실패를 나타내는 예외.
 * <p>
 * 기존에는 이런 실패가 {@code RuntimeException("...: " + e.getResponseBody())} 로 던져져
 * (1) 일반 fallback 핸들러 부재로 500 으로 누출되고, (2) k8s API 원문(responseBody)이 응답 body 에
 * 그대로 노출되었다. 본 예외로 감싸 {@link GlobalExceptionHandler} 가 502 로 매핑하며, 원문은
 * 로깅만 하고 클라이언트에는 노출하지 않는다. 원인 예외(보통 {@code ApiException})는 cause 로 보존한다.
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}
