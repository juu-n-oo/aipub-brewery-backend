package io.ten1010.dockerizerbackend.common.exception;

import io.kubernetes.client.openapi.ApiException;

/**
 * k8s {@link ApiException} 을 도메인 예외로 변환하는 단일 지점(SRV-5).
 * <p>
 * 기존에는 {@code 404 → ResourceNotFoundException, 그 외 → RuntimeException(responseBody)} 패턴이
 * 여러 클래스에 흩어져 있었다. 이를 일원화해 매핑 규칙과 메시지 포맷을 한 곳에서 관리한다.
 * 404 가 의미상 "정상적인 부재"가 아닌 경우(예: 로그 fallback 경계)에는 {@link #translateNon404} 를 쓴다.
 */
public final class K8sExceptions {

    private K8sExceptions() {
    }

    /**
     * 404 는 {@link ResourceNotFoundException}, 그 외는 {@link UpstreamServiceException} 으로 변환.
     *
     * @param resourceDescription 사용자에게 노출 가능한 리소스 설명(예: {@code "ImageBuild ns/name"}).
     *                            k8s 응답 원문은 cause 로만 보존되고 메시지에 포함하지 않는다.
     */
    public static RuntimeException translate(ApiException e, String resourceDescription) {
        if (e.getCode() == 404) {
            return new ResourceNotFoundException(resourceDescription + " not found");
        }
        return translateNon404(e, resourceDescription);
    }

    /**
     * 404 여부와 무관하게 항상 {@link UpstreamServiceException} 으로 변환(404 를 호출부가 별도 처리할 때).
     */
    public static UpstreamServiceException translateNon404(ApiException e, String resourceDescription) {
        return new UpstreamServiceException(
                "Kubernetes API error while accessing " + resourceDescription, e);
    }

}
