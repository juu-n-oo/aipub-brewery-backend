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

    sudo kubectl exec -n ${NAMESPACE} harbor-database-0 -i -- \
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

# Domain
DOCKERIZER_HOST=$(${YQ_COMMAND} -r '.domain.dockerizer_host' "$CONFIG_FILE")

# Agent
DATA_DOG_ENABLED=$(${YQ_COMMAND} -r '.agent.datadog // "false"' "$CONFIG_FILE")

# Application
LOGGING_LEVEL=$(${YQ_COMMAND} -r '.application.logging_level // "INFO"' "$CONFIG_FILE")

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

#==============================================================================
# Display Deployment Plan
#==============================================================================
log_step "Deployment Plan"
log_info "=========================================="
log_info "  Namespace:    ${NAMESPACE}"
log_info "  Registry:     ${IMAGE_BASE}"
log_info "  Dockerizer:   ${DOCKERIZER_HOST}"
log_info "  Database:     harbor-database/${DB_NAME}"
log_info "  Datadog:      ${DATA_DOG_ENABLED}"
log_info "=========================================="
log_info "  0. Database setup (CREATE DATABASE ${DB_NAME})"
log_info "  1. imagebuild-controller (${CONTROLLER_TAG})"
log_info "  2. dockerizer-backend        (${BACKEND_TAG})"
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

setup_db "CREATE DATABASE ${DB_NAME} (skip if exists)" \
    psql -U postgres -c \
    "SELECT 'exists' FROM pg_database WHERE datname = '${DB_NAME}'" \
    | grep -q 'exists' \
    && log_info "Database '${DB_NAME}' already exists, skipping" \
    || setup_db "CREATE DATABASE ${DB_NAME} OWNER aipub" \
        psql -U postgres -c "CREATE DATABASE ${DB_NAME} OWNER aipub;"

#==============================================================================
# Deploy: imagebuild-controller
#==============================================================================
log_step "Deploying imagebuild-controller"

deploy_helm_chart "imagebuild-controller" \
  --set image.repository="${CONTROLLER_IMAGE}" \
  --set image.tag="${CONTROLLER_TAG}" \
  --set applicationYaml.dockerizer.imagebuild.kanikoImage="${KANIKO_IMAGE}" \
  --set applicationYaml.logging.level.dockerizer="${LOGGING_LEVEL}" \
  --set agent.datadog="${DATA_DOG_ENABLED}"

#==============================================================================
# Deploy: dockerizer-backend
#==============================================================================
log_step "Deploying dockerizer-backend"

deploy_helm_chart "dockerizer-backend" \
  --set image.repository="${BACKEND_IMAGE}" \
  --set image.tag="${BACKEND_TAG}" \
  --set ingress.hosts[0].host="${DOCKERIZER_HOST}" \
  --set applicationYaml.spring.datasource.url="jdbc:postgresql://harbor-database.${NAMESPACE}.svc.cluster.local:5432/${DB_NAME}" \
  --set applicationYaml.spring.datasource.username="aipub" \
  --set applicationYaml.spring.datasource.password="${DB_PASSWORD}" \
  --set applicationYaml.logging.level.dockerizer="${LOGGING_LEVEL}" \
  --set agent.datadog="${DATA_DOG_ENABLED}"

#==============================================================================
# Completion
#==============================================================================
log_step "Installation Complete"
log_success "Dockerizer has been deployed to namespace '${NAMESPACE}'"
log_info "Access URL: https://${DOCKERIZER_HOST}"
