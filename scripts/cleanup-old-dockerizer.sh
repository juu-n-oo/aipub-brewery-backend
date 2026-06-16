#!/bin/bash

set -e  # Exit on error

#==============================================================================
# cleanup-old-dockerizer.sh
#
# Dockerizer → ImageKit 리브랜드 후, 개발/테스트 클러스터에 남은 구(舊) 'dockerizer'
# 이름의 리소스를 정리한다(데이터 폐기 전제). 정리 후 ./install.sh 로 ImageKit 을 재배포한다.
#
# 이 스크립트는 [작업 3] 구 Ingress path 제거 + [작업 4] 구 리소스 정리를 담당한다.
# 새 ImageKit 리소스 배포와 새 /imagekit Ingress path 추가는 install.sh 가 수행한다.
#
# 정리 대상:
#   1. Helm release : dockerizer-backend, dockerizer-web
#                     (imagebuild-controller 는 이름이 바뀌지 않아 보존 → install.sh 가 upgrade)
#   2. AIPub Ingress: backend service 가 dockerizer-* 인 path (구 /dockerizer 포함) 제거
#   3. ImageBuild Job: managed-by=dockerizer-controller 라벨이 붙은 구 Job
#   4. Database     : dockerizer (DROP) — ★데이터 영구 삭제, 폐기 전제
#   5. Secret       : dockerizer-backend-opensearch-certs
#
# 보존(건드리지 않음): CRD imagebuilds.aipub.ten1010.io, namespace, harbor-database,
#   aipub-* 리소스, DB user aipub/brewery.
#
# 사용법: sudo ./cleanup-old-dockerizer.sh --config config.json [--skip-confirmation]
#==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
trap cleanup_on_error EXIT

#------------------------------------------------------------------------------
# 구 이름 상수 (리브랜드 전 값 — 정리 대상 식별용)
#------------------------------------------------------------------------------
OLD_RELEASES=("dockerizer-backend" "dockerizer-web")
OLD_DB_NAME="dockerizer"
OLD_OPENSEARCH_SECRET="dockerizer-backend-opensearch-certs"
OLD_MANAGED_BY="dockerizer-controller"

#------------------------------------------------------------------------------
# yq 경로 (install.sh 와 동일 패턴)
#------------------------------------------------------------------------------
KI_ENV_BIN_PATH="/var/lib/ki-env/bin/bin"
if [ -x "${KI_ENV_BIN_PATH}/yq" ]; then
    YQ_COMMAND="${KI_ENV_BIN_PATH}/yq"
elif command -v yq &> /dev/null; then
    YQ_COMMAND="yq"
else
    log_error "yq command not found. Install yq or set KI_ENV_BIN_PATH."
    exit 1
fi

#------------------------------------------------------------------------------
# 인자 파싱
#------------------------------------------------------------------------------
SKIP_CONFIRMATION=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --config) CONFIG_FILE="$2"; shift 2;;
        --skip-confirmation) SKIP_CONFIRMATION=true; shift;;
        -h|--help)
            echo "Usage: sudo $0 --config <file> [--skip-confirmation]"
            exit 0;;
        *) log_error "Unknown option: $1"; echo "Use --help"; exit 1;;
    esac
done

[ -z "${CONFIG_FILE:-}" ] && { log_error "Configuration file not specified. Use --config <file>"; exit 1; }
[ ! -f "$CONFIG_FILE" ] && { log_error "Configuration file not found: $CONFIG_FILE"; exit 1; }

#------------------------------------------------------------------------------
# Pre-flight
#------------------------------------------------------------------------------
check_command kubectl
check_command helm
check_command jq

NAMESPACE=$(${YQ_COMMAND} -r '.namespace' "$CONFIG_FILE")
AIPUB_INGRESS_NAME=$(${YQ_COMMAND} -r '.domain.aipub_ingress_name // "aipub-backend-adapter"' "$CONFIG_FILE")
BACKUP_DIR="${SCRIPT_DIR}/backups/${NAMESPACE}/cleanup-$(date +%Y%m%d_%H%M%S)"

#------------------------------------------------------------------------------
# 확인 헬퍼 (--skip-confirmation 이면 항상 yes)
#------------------------------------------------------------------------------
confirm() {
    [ "$SKIP_CONFIRMATION" = true ] && return 0
    local msg=$1
    local yn
    read -p "${msg} (yes/no) [no]: " yn < /dev/tty
    yn=${yn:-no}
    case $yn in
        [Yy]* ) return 0;;
        * ) return 1;;
    esac
}

#==============================================================================
# Plan
#==============================================================================
log_step "Cleanup Plan"
log_info "=========================================="
log_info "  Namespace:    ${NAMESPACE}"
log_info "  AIPub Ingress: ${AIPUB_INGRESS_NAME}"
log_info "=========================================="
log_warn "이 스크립트는 구(舊) 'dockerizer' 리소스를 영구 삭제합니다 (데이터 폐기 전제)."
log_info "  1. Helm release : ${OLD_RELEASES[*]}"
log_info "  2. AIPub Ingress: dockerizer-* path 제거"
log_info "  3. ImageBuild Job: managed-by=${OLD_MANAGED_BY}"
log_info "  4. Database     : DROP ${OLD_DB_NAME}"
log_info "  5. Secret       : ${OLD_OPENSEARCH_SECRET}"
log_info "=========================================="
if ! confirm "위 정리를 진행하시겠습니까?"; then
    log_info "취소되었습니다."
    trap - EXIT
    exit 0
fi

#==============================================================================
# 1. 구 Helm release uninstall
#==============================================================================
log_step "1. 구 Helm release 정리"
for rel in "${OLD_RELEASES[@]}"; do
    if sudo helm status -n "${NAMESPACE}" "${rel}" &> /dev/null; then
        if confirm "helm uninstall ${rel}?"; then
            sudo helm uninstall -n "${NAMESPACE}" "${rel}" \
                && log_success "uninstalled ${rel}" \
                || log_warn "helm uninstall ${rel} 실패 (수동 확인 필요)"
        else
            log_info "skip ${rel}"
        fi
    else
        log_info "release '${rel}' 없음 (skip)"
    fi
done
log_info "참고: imagebuild-controller 는 이름 불변 → 보존합니다 (install.sh 가 upgrade)."

#==============================================================================
# 2. AIPub Ingress 구 path 제거 (backend service 가 dockerizer-* 인 path)
#==============================================================================
log_step "2. AIPub Ingress 구 path 제거"
if sudo kubectl get ingress -n "${NAMESPACE}" "${AIPUB_INGRESS_NAME}" &> /dev/null; then
    # 구 path 인덱스를 내림차순으로 수집(삭제 시 인덱스 시프트 방지)
    old_idx=$(sudo kubectl get ingress -n "${NAMESPACE}" "${AIPUB_INGRESS_NAME}" -o json \
        | jq -r '.spec.rules[0].http.paths
                 | to_entries
                 | map(select(.value.backend.service.name | test("^dockerizer-")))
                 | map(.key) | sort | reverse | .[]' 2>/dev/null)
    if [ -n "${old_idx}" ]; then
        cnt=$(echo ${old_idx} | wc -w | tr -d ' ')
        if confirm "Ingress 에서 dockerizer-* path ${cnt}개 제거?"; then
            mkdir -p "${BACKUP_DIR}"
            sudo kubectl get ingress -n "${NAMESPACE}" "${AIPUB_INGRESS_NAME}" -o yaml \
                > "${BACKUP_DIR}/ingress-before.yaml"
            for i in ${old_idx}; do
                sudo kubectl patch ingress -n "${NAMESPACE}" "${AIPUB_INGRESS_NAME}" \
                    --type=json -p "[{\"op\":\"remove\",\"path\":\"/spec/rules/0/http/paths/${i}\"}]"
            done
            sudo kubectl get ingress -n "${NAMESPACE}" "${AIPUB_INGRESS_NAME}" -o yaml \
                > "${BACKUP_DIR}/ingress-after.yaml"
            log_success "Ingress 구 path ${cnt}개 제거 (backup: ${BACKUP_DIR}/ingress-before.yaml)"
        else
            log_info "skip Ingress"
        fi
    else
        log_info "dockerizer-* path 없음 (skip)"
    fi
else
    log_warn "Ingress '${AIPUB_INGRESS_NAME}' 없음 (skip)"
fi

#==============================================================================
# 3. 구 ImageBuild Job 정리
#==============================================================================
log_step "3. 구 ImageBuild Job 정리 (managed-by=${OLD_MANAGED_BY})"
job_cnt=$(sudo kubectl get jobs -n "${NAMESPACE}" \
    -l "app.kubernetes.io/managed-by=${OLD_MANAGED_BY}" -o name 2>/dev/null | grep -c "^" || true)
if [ "${job_cnt}" != "0" ]; then
    if confirm "구 Job ${job_cnt}개 삭제?"; then
        sudo kubectl delete jobs -n "${NAMESPACE}" \
            -l "app.kubernetes.io/managed-by=${OLD_MANAGED_BY}" \
            && log_success "구 Job 삭제 완료" \
            || log_warn "Job 삭제 일부 실패"
    else
        log_info "skip Job"
    fi
else
    log_info "구 Job 없음 (skip)"
fi

#==============================================================================
# 4. 구 Database DROP (★데이터 영구 삭제)
#==============================================================================
log_step "4. 구 Database '${OLD_DB_NAME}' 삭제"
if sudo kubectl get pod -n "${NAMESPACE}" harbor-database-0 &> /dev/null; then
    HARBOR_POSTGRES=$(get_k8s_secret "harbor-database" "${NAMESPACE}" "POSTGRES_PASSWORD")
    db_exists=$(sudo kubectl exec -n "${NAMESPACE}" harbor-database-0 -c database -- \
        env PGPASSWORD="${HARBOR_POSTGRES}" \
        psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '${OLD_DB_NAME}'" 2>/dev/null \
        | tr -d '[:space:]')
    if [ "${db_exists}" = "1" ]; then
        if confirm "DROP DATABASE ${OLD_DB_NAME} (★데이터 영구 삭제)?"; then
            # PG13+ 는 WITH (FORCE) 로 활성 연결을 끊고 삭제. 실패 시 일반 DROP 재시도.
            sudo kubectl exec -n "${NAMESPACE}" harbor-database-0 -c database -- \
                env PGPASSWORD="${HARBOR_POSTGRES}" \
                psql -U postgres -c "DROP DATABASE IF EXISTS ${OLD_DB_NAME} WITH (FORCE);" \
                && log_success "DB ${OLD_DB_NAME} 삭제" \
                || {
                    log_warn "WITH (FORCE) 실패 — 일반 DROP 재시도"
                    sudo kubectl exec -n "${NAMESPACE}" harbor-database-0 -c database -- \
                        env PGPASSWORD="${HARBOR_POSTGRES}" \
                        psql -U postgres -c "DROP DATABASE IF EXISTS ${OLD_DB_NAME};" \
                        && log_success "DB ${OLD_DB_NAME} 삭제" \
                        || log_warn "DROP 실패 — 활성 연결 확인 후 수동 삭제 필요"
                }
        else
            log_info "skip DB"
        fi
    else
        log_info "DB '${OLD_DB_NAME}' 없음 (skip)"
    fi
else
    log_warn "harbor-database-0 pod 없음 (skip DB)"
fi

#==============================================================================
# 5. 구 OpenSearch CA secret 삭제
#==============================================================================
log_step "5. 구 OpenSearch CA secret 삭제"
if sudo kubectl get secret -n "${NAMESPACE}" "${OLD_OPENSEARCH_SECRET}" &> /dev/null; then
    if confirm "secret ${OLD_OPENSEARCH_SECRET} 삭제?"; then
        sudo kubectl delete secret -n "${NAMESPACE}" "${OLD_OPENSEARCH_SECRET}" \
            && log_success "secret 삭제" \
            || log_warn "secret 삭제 실패"
    else
        log_info "skip secret"
    fi
else
    log_info "secret '${OLD_OPENSEARCH_SECRET}' 없음 (skip)"
fi

#==============================================================================
# 완료 + 재배포 안내
#==============================================================================
log_step "정리 완료"
log_success "구 dockerizer 리소스 정리가 끝났습니다."
log_info ""
log_info "이제 새 ImageKit 리소스를 배포하세요:"
log_info "  sudo ${SCRIPT_DIR}/install.sh --config ${CONFIG_FILE}"
log_info "  (이미지 재빌드/푸시가 필요하면 --build, 무인 배포는 --skip-confirmation 추가)"
log_info ""
log_info "install.sh 가 자동으로 수행하는 것:"
log_info "  - DB 'imagekit' 생성 + 스키마 적용 (sql/ImageKit_*.sql)"
log_info "  - imagebuild-controller upgrade, imagekit-backend 설치"
log_info "  - AIPub Ingress 에 /imagekit 및 imagekit-backend API path 추가"
log_info "  - imagekit-web 은 프론트 배포 스크립트(deploy/ 또는 build-and-deploy)로 별도 배포"

trap - EXIT
