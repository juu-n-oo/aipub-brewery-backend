#!/bin/bash

set -e  # Exit on error

#==============================================================================
# Dockerizer Install Script
# AIPub 설치 패턴과 동일한 방식으로 Helm 차트를 배포한다.
# 사용법: sudo ./install.sh --config config.json [--skip-confirmation]
#==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="${SCRIPT_DIR}/../helm"

source "${SCRIPT_DIR}/common.sh"
trap cleanup_on_error EXIT

#==============================================================================
# yq 경로 설정 (ki-env가 있으면 사용, 없으면 PATH에서 탐색)
#==============================================================================
KI_ENV_BIN_PATH="/var/lib/ki-env/bin/bin"
if [ -x "${KI_ENV_BIN_PATH}/yq" ]; then
    YQ_COMMAND="${KI_ENV_BIN_PATH}/yq"
elif command -v yq &> /dev/null; then
    YQ_COMMAND="yq"
else
    log_error "yq command not found. Install yq or set KI_ENV_BIN_PATH."
    exit 1
fi

#==============================================================================
# Command Line Arguments
#==============================================================================
SKIP_CONFIRMATION=false
BUILD_IMAGES=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build)
            BUILD_IMAGES=true
            shift
            ;;
        --skip-confirmation)
            SKIP_CONFIRMATION=true
            shift
            ;;
        --config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --config <file>          Specify configuration JSON file"
            echo "  --build                  Build and push Docker images before deploying"
            echo "  --skip-confirmation      Skip deployment confirmation prompts"
            echo "  -h, --help              Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

if [ -z "${CONFIG_FILE:-}" ]; then
    log_error "Configuration file not specified. Use --config <file>"
    exit 1
fi
if [ ! -f "$CONFIG_FILE" ]; then
    log_error "Configuration file not found: $CONFIG_FILE"
    exit 1
fi

log_info "Loading configuration from: $CONFIG_FILE"

#==============================================================================
# Confirmation Helper
#==============================================================================
confirm_deployment() {
    local chart_name=$1

    if [ "$SKIP_CONFIRMATION" = true ]; then
        return 0
    fi

    while true; do
        read -p "Deploy ${chart_name}? (yes/no) [no]: " yn < /dev/tty
        yn=${yn:-no}
        case $yn in
            [Yy]* | [Yy][Ee][Ss]* ) return 0;;
            [Nn]* | [Nn][Oo]* ) return 1;;
            * ) echo "Please answer yes or no.";;
        esac
    done
}

#==============================================================================
# DB Setup Helper (harbor-database pod에서 실행)
#==============================================================================
setup_db() {
    local description=$1
    shift

    if [ "$SKIP_CONFIRMATION" = true ]; then
        log_info "Executing: ${description}"
    else
        while true; do
            read -p "Execute ${description}? (yes/no) [no]: " yn < /dev/tty
            yn=${yn:-no}
            case $yn in
                [Yy]* | [Yy][Ee][Ss]* )
                    log_info "Executing: ${description}"
                    break
                    ;;
                [Nn]* | [Nn][Oo]* )
                    log_info "Skipped ${description}"
                    return 0
                    ;;
                * ) echo "Please answer yes or no.";;
            esac
        done
    fi

    sudo kubectl exec -n ${NAMESPACE} harbor-database-0 -c database -- \
      env PGPASSWORD="${HARBOR_POSTGRES}" \
      "$@"

    if [ $? -eq 0 ]; then
        log_success "${description} completed"
    else
        log_error "Failed: ${description}"
        return 1
    fi
}

#==============================================================================
# Helm Deploy (with backup & change report)
#==============================================================================
deploy_helm_chart() {
    local chart_name=$1
    shift

    if ! confirm_deployment "${chart_name}"; then
        log_info "Skipped ${chart_name}"
        return 0
    fi

    local backup_dir="${BACKUP_BASE_DIR}/${chart_name}"
    mkdir -p "${backup_dir}"

    local before_file="${backup_dir}/before.yaml"
    local after_file="${backup_dir}/after.yaml"
    local report_file="${backup_dir}/change-report.txt"
    local is_upgrade=false

    if sudo helm status -n ${NAMESPACE} ${chart_name} &> /dev/null; then
        is_upgrade=true
        log_info "Backing up existing release: ${chart_name}"
        sudo helm get manifest -n ${NAMESPACE} ${chart_name} > "${before_file}"
        sudo helm get values -n ${NAMESPACE} ${chart_name} > "${backup_dir}/values-before.yaml"
        log_success "Backup saved: ${backup_dir}"
    fi

    log_info "Deploying ${chart_name}..."

    sudo helm upgrade -n ${NAMESPACE} ${chart_name} "${CHART_DIR}/${chart_name}/" \
        --install \
        "$@"

    if [ $? -eq 0 ]; then
        log_success "${chart_name} deployed successfully"

        if [ "$is_upgrade" = true ]; then
            log_info "Restarting workloads for existing release ${chart_name}..."
            if sudo kubectl rollout restart deployment -n ${NAMESPACE} -l "app.kubernetes.io/instance=${chart_name}"; then
                sudo kubectl rollout status deployment -n ${NAMESPACE} -l "app.kubernetes.io/instance=${chart_name}" --timeout=300s || true
                log_success "${chart_name} workloads restarted"
            else
                log_warn "No matching workloads to restart for ${chart_name}"
            fi
        fi

        sudo helm get manifest -n ${NAMESPACE} ${chart_name} > "${after_file}"
        sudo helm get values -n ${NAMESPACE} ${chart_name} > "${backup_dir}/values-after.yaml"

        if [ "$is_upgrade" = true ]; then
            {
                echo "=============================================="
                echo "  Change Report: ${chart_name}"
                echo "  Date: $(date '+%Y-%m-%d %H:%M:%S')"
                echo "  Namespace: ${NAMESPACE}"
                echo "=============================================="
                echo ""
                echo "--- Manifest Changes ---"
                diff -u "${before_file}" "${after_file}" || true
                echo ""
                echo "--- Values Changes ---"
                diff -u "${backup_dir}/values-before.yaml" "${backup_dir}/values-after.yaml" || true
            } > "${report_file}"
        else
            {
                echo "=============================================="
                echo "  Change Report: ${chart_name}"
                echo "  Date: $(date '+%Y-%m-%d %H:%M:%S')"
                echo "  Namespace: ${NAMESPACE}"
                echo "  Type: 신규 설치"
                echo "=============================================="
                echo ""
                echo "신규 설치 - 이전 릴리즈 없음"
                echo ""
                echo "--- Installed Manifest ---"
                cat "${after_file}"
            } > "${report_file}"
        fi
        log_info "Change report: ${report_file}"
    else
        log_error "Failed to deploy ${chart_name}"
        exit 1
    fi
}

#==============================================================================
# Load Configuration
#==============================================================================
log_step "Loading configuration"

NAMESPACE=$(${YQ_COMMAND} -r '.namespace' "$CONFIG_FILE")

# Version / Images
IMAGE_BASE=$(${YQ_COMMAND} -r '.version.image_base' "$CONFIG_FILE")
BACKEND_TAG=$(${YQ_COMMAND} -r '.version.backend_tag' "$CONFIG_FILE")
CONTROLLER_TAG=$(${YQ_COMMAND} -r '.version.controller_tag' "$CONFIG_FILE")
KANIKO_VERSION=$(${YQ_COMMAND} -r '.version.kaniko_version // "v1.24.0"' "$CONFIG_FILE")

BACKEND_IMAGE="${IMAGE_BASE}/dockerizer-backend"
CONTROLLER_IMAGE="${IMAGE_BASE}/imagebuild-controller"
KANIKO_IMAGE="${IMAGE_BASE}/kaniko-executor:${KANIKO_VERSION}"

# Domain (AIPub Ingress sub path 방식)
AIPUB_HOST=$(${YQ_COMMAND} -r '.domain.aipub_host' "$CONFIG_FILE")
AIPUB_INGRESS_NAME=$(${YQ_COMMAND} -r '.domain.aipub_ingress_name // "aipub-backend-adapter"' "$CONFIG_FILE")

# Agent
DATA_DOG_ENABLED=$(${YQ_COMMAND} -r '.agent.datadog // "false"' "$CONFIG_FILE")

# Application
LOGGING_LEVEL=$(${YQ_COMMAND} -r '.application.logging_level // "INFO"' "$CONFIG_FILE")
# 빌드 Job/Pod 완료 후 GC 까지 시간(초). 이후 로그는 OpenSearch fallback 으로만 조회 가능.
JOB_TTL_SECONDS=$(${YQ_COMMAND} -r '.application.job_ttl_seconds // "3600"' "$CONFIG_FILE")

# Database
DB_NAME=$(${YQ_COMMAND} -r '.database.name // "dockerizer"' "$CONFIG_FILE")

BACKUP_BASE_DIR="${SCRIPT_DIR}/backups/${NAMESPACE}/$(date +%Y%m%d_%H%M%S)"

#==============================================================================
# Pre-flight Checks
#==============================================================================
log_step "Pre-flight checks"

check_command kubectl
check_command helm
check_namespace "${NAMESPACE}"

#==============================================================================
# Retrieve Secrets from Kubernetes
#==============================================================================
log_step "Retrieving secrets"

HARBOR_POSTGRES=$(get_k8s_secret "harbor-database" "${NAMESPACE}" "POSTGRES_PASSWORD")
DB_PASSWORD=$(sudo kubectl get secret -n ${NAMESPACE} aipub-backend-api-envs \
    -o=jsonpath='{.data.SPRING_DATASOURCE_PASSWORD}' 2>/dev/null | base64 -d)
if [ -z "${DB_PASSWORD}" ]; then
    log_warn "Could not retrieve aipub datasource password from aipub-backend-api-envs, falling back to harbor-database password"
    DB_PASSWORD="${HARBOR_POSTGRES}"
fi
log_info "DB Password: ${DB_PASSWORD:0:5}***"

#------------------------------------------------------------------------------
# OpenSearch (build log fallback) secrets — ns aipub-monitoring 의 시크릿을
# backend ns 로 복제한다. 모니터링 스택이 없으면 graceful 하게 skip.
#------------------------------------------------------------------------------
OPENSEARCH_MONITORING_NS="aipub-monitoring"
OPENSEARCH_CREDS_SECRET="opensearch-cluster-master-credentials"
OPENSEARCH_CERTS_SECRET="opensearch-cluster-master-certs"
OPENSEARCH_BACKEND_CERTS_SECRET="dockerizer-backend-opensearch-certs"
OPENSEARCH_ENABLED=false
OPENSEARCH_USERNAME=""
OPENSEARCH_PASSWORD=""
OPENSEARCH_URL="https://opensearch-cluster-master.${OPENSEARCH_MONITORING_NS}:9200"

if sudo kubectl get secret -n "${OPENSEARCH_MONITORING_NS}" "${OPENSEARCH_CREDS_SECRET}" &> /dev/null \
   && sudo kubectl get secret -n "${OPENSEARCH_MONITORING_NS}" "${OPENSEARCH_CERTS_SECRET}" &> /dev/null; then
    OPENSEARCH_USERNAME=$(get_k8s_secret "${OPENSEARCH_CREDS_SECRET}" "${OPENSEARCH_MONITORING_NS}" "username")
    OPENSEARCH_PASSWORD=$(get_k8s_secret "${OPENSEARCH_CREDS_SECRET}" "${OPENSEARCH_MONITORING_NS}" "password")

    OS_CA_TMP="$(mktemp)"
    sudo kubectl get secret -n "${OPENSEARCH_MONITORING_NS}" "${OPENSEARCH_CERTS_SECRET}" \
        -o jsonpath='{.data.ca\.crt}' | base64 -d > "${OS_CA_TMP}"

    if [ -s "${OS_CA_TMP}" ]; then
        sudo kubectl create secret generic "${OPENSEARCH_BACKEND_CERTS_SECRET}" -n "${NAMESPACE}" \
            --from-file=ca.crt="${OS_CA_TMP}" --dry-run=client -o yaml | sudo kubectl apply -f -
        OPENSEARCH_ENABLED=true
        log_success "OpenSearch fallback enabled (CA secret '${OPENSEARCH_BACKEND_CERTS_SECRET}' created in ${NAMESPACE})"
        log_info "OpenSearch user: ${OPENSEARCH_USERNAME}, password: ${OPENSEARCH_PASSWORD:0:3}***"
    else
        log_warn "OpenSearch certs secret '${OPENSEARCH_CERTS_SECRET}' has empty ca.crt; OpenSearch fallback disabled"
    fi
    rm -f "${OS_CA_TMP}"
else
    log_warn "OpenSearch monitoring secrets not found in ns '${OPENSEARCH_MONITORING_NS}'; build log OpenSearch fallback disabled"
fi

#==============================================================================
# Display Deployment Plan
#==============================================================================
log_step "Deployment Plan"
log_info "=========================================="
log_info "  Namespace:    ${NAMESPACE}"
log_info "  Registry:     ${IMAGE_BASE}"
log_info "  AIPub Host:   ${AIPUB_HOST}"
log_info "  AIPub Ingress: ${AIPUB_INGRESS_NAME}"
log_info "  Database:     harbor-database/${DB_NAME}"
log_info "  Datadog:      ${DATA_DOG_ENABLED}"
log_info "=========================================="
log_info "  0. Database setup (CREATE DATABASE ${DB_NAME})"
log_info "  1. imagebuild-controller (${CONTROLLER_TAG})"
log_info "  2. dockerizer-backend        (${BACKEND_TAG})"
log_info "  3. AIPub Ingress patch       (${AIPUB_INGRESS_NAME})"
log_info "=========================================="
log_info ""

if [ "$SKIP_CONFIRMATION" = false ]; then
    log_info "You will be prompted to confirm each deployment."
    log_info "Tip: Use --skip-confirmation to deploy all without prompts"
    log_info ""
fi

#==============================================================================
# Image Build & Push (optional)
#==============================================================================
if [ "$BUILD_IMAGES" = true ]; then
    log_step "Building and pushing Docker images"

    check_command docker

    cd "${SCRIPT_DIR}/.."
    log_info "Building JAR artifacts..."
    ./gradlew clean :dockerizer-backend-server:bootJar :imagebuild-controller:bootJar -x test -x asciidoctor

    BACKEND_IMAGE_FULL="${IMAGE_BASE}/dockerizer-backend:${BACKEND_TAG}"
    CONTROLLER_IMAGE_FULL="${IMAGE_BASE}/imagebuild-controller:${CONTROLLER_TAG}"

    log_info "Building image: ${BACKEND_IMAGE_FULL}"
    sudo docker build --platform linux/amd64 \
      -t "${BACKEND_IMAGE_FULL}" \
      -f dockerizer-backend-server/Dockerfile .

    log_info "Building image: ${CONTROLLER_IMAGE_FULL}"
    sudo docker build --platform linux/amd64 \
      -t "${CONTROLLER_IMAGE_FULL}" \
      -f imagebuild-controller/Dockerfile .

    log_info "Pushing images..."
    sudo docker push "${BACKEND_IMAGE_FULL}"
    sudo docker push "${CONTROLLER_IMAGE_FULL}"
    log_success "Images built and pushed"

    cd "${SCRIPT_DIR}"
fi

#==============================================================================
# Database Setup: CREATE DATABASE on harbor-database
#==============================================================================
log_step "Database setup"

DB_EXISTS=$(sudo kubectl exec -n ${NAMESPACE} harbor-database-0 -c database -- \
    env PGPASSWORD="${HARBOR_POSTGRES}" \
    psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" 2>&1)
DB_EXISTS=$(echo "${DB_EXISTS}" | tr -d '[:space:]')
log_info "DB existence check result: '${DB_EXISTS}'"

if [ "${DB_EXISTS}" = "1" ]; then
    log_info "Database '${DB_NAME}' already exists, skipping"
else
    setup_db "CREATE DATABASE ${DB_NAME} OWNER aipub" \
        psql -U postgres -c "CREATE DATABASE ${DB_NAME} OWNER aipub;"
fi

#==============================================================================
# Database Schema: Run SQL migrations
#==============================================================================
log_step "Database schema initialization"

SQL_DIR="${SCRIPT_DIR}/../sql"
if [ -d "${SQL_DIR}" ]; then
    for sql_file in $(ls "${SQL_DIR}"/*.sql 2>/dev/null | sort); do
        sql_filename=$(basename "${sql_file}")
        log_info "Executing: ${sql_filename}"
        sudo kubectl exec -i -n ${NAMESPACE} harbor-database-0 -c database -- \
            env PGPASSWORD="${DB_PASSWORD}" \
            psql -U aipub -d "${DB_NAME}" < "${sql_file}"
        if [ $? -eq 0 ]; then
            log_success "${sql_filename} applied"
        else
            log_error "Failed to apply ${sql_filename}"
            exit 1
        fi
    done
else
    log_warn "No SQL directory found at ${SQL_DIR}, skipping schema initialization"
fi

#==============================================================================
# Deploy: imagebuild-controller
#==============================================================================
log_step "Deploying imagebuild-controller"

deploy_helm_chart "imagebuild-controller" \
  --set image.repository="${CONTROLLER_IMAGE}" \
  --set image.tag="${CONTROLLER_TAG}" \
  --set applicationYaml.dockerizer.imagebuild.kanikoImage="${KANIKO_IMAGE}" \
  --set applicationYaml.dockerizer.imagebuild.jobTtlSeconds="${JOB_TTL_SECONDS}" \
  --set applicationYaml.logging.level.dockerizer="${LOGGING_LEVEL}" \
  --set agent.datadog="${DATA_DOG_ENABLED}"

#==============================================================================
# Deploy: dockerizer-backend
#==============================================================================
log_step "Deploying dockerizer-backend"

# OpenSearch fallback 활성화 시 --set 인자 + CA 볼륨 와이어링을 동적으로 구성한다.
# values.yaml 의 기본 volumes/volumeMounts(ca-certs) 를 보존하면서 opensearch-certs 를 추가하기 위해
# --set-json 으로 전체 리스트를 덮어쓴다.
OPENSEARCH_SET_ARGS=()
if [ "${OPENSEARCH_ENABLED}" = true ]; then
    OPENSEARCH_SET_ARGS=(
        --set applicationYaml.dockerizer.opensearch.enabled=true
        --set applicationYaml.dockerizer.opensearch.url="${OPENSEARCH_URL}"
        --set applicationYaml.dockerizer.opensearch.username="${OPENSEARCH_USERNAME}"
        --set applicationYaml.dockerizer.opensearch.password="${OPENSEARCH_PASSWORD}"
        --set applicationYaml.dockerizer.opensearch.caCertPath="/opensearch-certs/ca.crt"
        --set-json volumes="[{\"name\":\"ca-certs\",\"secret\":{\"secretName\":\"custom-ca-certs\"}},{\"name\":\"opensearch-certs\",\"secret\":{\"secretName\":\"${OPENSEARCH_BACKEND_CERTS_SECRET}\"}}]"
        --set-json volumeMounts="[{\"name\":\"ca-certs\",\"mountPath\":\"/certificates\",\"readOnly\":true},{\"name\":\"opensearch-certs\",\"mountPath\":\"/opensearch-certs\",\"readOnly\":true}]"
    )
fi

deploy_helm_chart "dockerizer-backend" \
  --set image.repository="${BACKEND_IMAGE}" \
  --set image.tag="${BACKEND_TAG}" \
  --set applicationYaml.spring.datasource.url="jdbc:postgresql://harbor-database.${NAMESPACE}.svc.cluster.local:5432/${DB_NAME}" \
  --set applicationYaml.spring.datasource.username="aipub" \
  --set applicationYaml.spring.datasource.password="${DB_PASSWORD}" \
  --set applicationYaml.logging.level.dockerizer="${LOGGING_LEVEL}" \
  --set agent.datadog="${DATA_DOG_ENABLED}" \
  "${OPENSEARCH_SET_ARGS[@]}"

#==============================================================================
# Patch AIPub Ingress: add Dockerizer routing paths
#==============================================================================
log_step "Patching AIPub Ingress (${AIPUB_INGRESS_NAME})"

patch_aipub_ingress() {
    if ! confirm_deployment "AIPub Ingress patch"; then
        log_info "Skipped AIPub Ingress patch"
        return 0
    fi

    local existing_paths
    existing_paths=$(sudo kubectl get ingress -n ${NAMESPACE} ${AIPUB_INGRESS_NAME} \
        -o jsonpath='{.spec.rules[0].http.paths[*].path}' 2>/dev/null)

    if echo "${existing_paths}" | grep -q "/api/v1alpha1/dockerfiles"; then
        log_info "Dockerizer paths already present in AIPub Ingress, skipping patch"
        return 0
    fi

    local backup_dir="${BACKUP_BASE_DIR}/aipub-ingress"
    mkdir -p "${backup_dir}"

    log_info "Backing up current AIPub Ingress..."
    sudo kubectl get ingress -n ${NAMESPACE} ${AIPUB_INGRESS_NAME} -o yaml > "${backup_dir}/before.yaml"

    # Dockerizer API paths — must be inserted before the generic /api path
    # so that specific dockerizer resource paths take priority.
    local PATCH='[
      {"op":"add","path":"/spec/rules/0/http/paths/-","value":{
        "path":"/api/v1alpha1/dockerfiles","pathType":"Prefix",
        "backend":{"service":{"name":"dockerizer-backend","port":{"number":8080}}}
      }},
      {"op":"add","path":"/spec/rules/0/http/paths/-","value":{
        "path":"/api/v1alpha1/builds","pathType":"Prefix",
        "backend":{"service":{"name":"dockerizer-backend","port":{"number":8080}}}
      }},
      {"op":"add","path":"/spec/rules/0/http/paths/-","value":{
        "path":"/api/v1alpha1/volumes","pathType":"Prefix",
        "backend":{"service":{"name":"dockerizer-backend","port":{"number":8080}}}
      }},
      {"op":"add","path":"/spec/rules/0/http/paths/-","value":{
        "path":"/api/v1alpha1/registries","pathType":"Prefix",
        "backend":{"service":{"name":"dockerizer-backend","port":{"number":8080}}}
      }},
      {"op":"add","path":"/spec/rules/0/http/paths/-","value":{
        "path":"/dockerizer","pathType":"Prefix",
        "backend":{"service":{"name":"dockerizer-web","port":{"number":80}}}
      }}
    ]'

    sudo kubectl patch ingress -n ${NAMESPACE} ${AIPUB_INGRESS_NAME} \
        --type='json' \
        -p "${PATCH}"

    if [ $? -eq 0 ]; then
        log_success "AIPub Ingress patched successfully"
        sudo kubectl get ingress -n ${NAMESPACE} ${AIPUB_INGRESS_NAME} -o yaml > "${backup_dir}/after.yaml"
        {
            echo "=============================================="
            echo "  Change Report: AIPub Ingress Patch"
            echo "  Date: $(date '+%Y-%m-%d %H:%M:%S')"
            echo "  Namespace: ${NAMESPACE}"
            echo "=============================================="
            echo ""
            diff -u "${backup_dir}/before.yaml" "${backup_dir}/after.yaml" || true
        } > "${backup_dir}/change-report.txt"
        log_info "Change report: ${backup_dir}/change-report.txt"
    else
        log_error "Failed to patch AIPub Ingress"
        log_info "Backup saved at: ${backup_dir}/before.yaml"
        exit 1
    fi
}

patch_aipub_ingress

#==============================================================================
# Completion
#==============================================================================
log_step "Installation Complete"
log_success "Dockerizer has been deployed to namespace '${NAMESPACE}'"
log_info "Access URL: https://${AIPUB_HOST}/dockerizer"
