#!/bin/bash
set -e

# ============================================================
# ImageKit Backend Deploy Script
# Builds images, pushes Kaniko executor to Harbor, and deploys
# via Helm to the target Kubernetes cluster.
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="${NAMESPACE:-aipub}"

# --- Registry & image configuration ---
REGISTRY="${REGISTRY:-registry.ten1010.io:8443}"
REGISTRY_PROJECT="${REGISTRY_PROJECT:-aipub}"
IMAGE_BASE="${REGISTRY}/${REGISTRY_PROJECT}"

BACKEND_IMAGE="${IMAGE_BASE}/imagekit-backend"
CONTROLLER_IMAGE="${IMAGE_BASE}/imagebuild-controller"
BACKEND_TAG="${BACKEND_TAG:-0.1.0}"
CONTROLLER_TAG="${CONTROLLER_TAG:-0.1.0}"

KANIKO_VERSION="v1.24.0"
KANIKO_SOURCE_IMAGE="gcr.io/kaniko-project/executor:${KANIKO_VERSION}"
KANIKO_TARGET_IMAGE="${IMAGE_BASE}/kaniko-executor:${KANIKO_VERSION}"

# ============================================================
# Functions
# ============================================================

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

build_and_push() {
  local module="$1"
  local image_name="$2"
  local tag="$3"

  log "Building ${module} JAR..."
  cd "${SCRIPT_DIR}"
  ./gradlew ":${module}:bootJar"

  log "Building Docker image: ${image_name}:${tag}"
  docker build -t "${image_name}:${tag}" -f "${module}/Dockerfile" .

  log "Pushing ${image_name}:${tag} to registry"
  docker push "${image_name}:${tag}"
}

push_kaniko_to_registry() {
  log "Preparing Kaniko executor image (${KANIKO_VERSION}) for internal registry"

  if docker image inspect "${KANIKO_SOURCE_IMAGE}" &>/dev/null; then
    log "Found ${KANIKO_SOURCE_IMAGE} locally"
  elif docker pull "${KANIKO_SOURCE_IMAGE}" 2>/dev/null; then
    log "Pulled ${KANIKO_SOURCE_IMAGE} from external registry"
  else
    log "ERROR: Cannot pull Kaniko image ${KANIKO_SOURCE_IMAGE}."
    log "Please pull it manually on a machine with internet access and 'docker save/load' it here."
    exit 1
  fi

  docker tag "${KANIKO_SOURCE_IMAGE}" "${KANIKO_TARGET_IMAGE}"
  log "Pushing ${KANIKO_TARGET_IMAGE}"
  docker push "${KANIKO_TARGET_IMAGE}"
  log "Kaniko executor image pushed successfully"
}

deploy_helm_chart() {
  local chart_name="$1"
  shift

  log "Deploying Helm chart: ${chart_name}"
  sudo helm upgrade -n "${NAMESPACE}" "${chart_name}" "${SCRIPT_DIR}/helm/${chart_name}/" \
    --install \
    "$@"
}

# ============================================================
# Main
# ============================================================

log "=== ImageKit Backend Deploy Start ==="

# 1. Build and push application images
build_and_push "backend-server" "${BACKEND_IMAGE}" "${BACKEND_TAG}"
build_and_push "imagebuild-controller" "${CONTROLLER_IMAGE}" "${CONTROLLER_TAG}"

# 2. Push Kaniko executor image to internal registry
push_kaniko_to_registry

# 3. Deploy imagebuild-controller
deploy_helm_chart "imagebuild-controller" \
  --set image.repository="${CONTROLLER_IMAGE}" \
  --set image.tag="${CONTROLLER_TAG}" \
  --set applicationYaml.imagekit.imagebuild.kanikoImage="${KANIKO_TARGET_IMAGE}"

# 4. Deploy backend-server
deploy_helm_chart "backend-server" \
  --set image.repository="${BACKEND_IMAGE}" \
  --set image.tag="${BACKEND_TAG}" \
  "$@"

log "=== ImageKit Backend Deploy Complete ==="
