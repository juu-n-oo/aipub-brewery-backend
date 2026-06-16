#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."

# ---- Defaults ----
REGISTRY="${REGISTRY:-aipub-harbor.cluster7.idc1.ten1010.io}"
REPOSITORY_BASE="${REPOSITORY_BASE:-aipub}"
TAG="${TAG:-$(cd "${PROJECT_ROOT}" && git describe --tags --always --dirty 2>/dev/null || echo "dev")}"
PLATFORM="${PLATFORM:-linux/amd64}"

BACKEND_IMAGE="${REGISTRY}/${REPOSITORY_BASE}/imagekit-backend:${TAG}"
CONTROLLER_IMAGE="${REGISTRY}/${REPOSITORY_BASE}/imagebuild-controller:${TAG}"

#==============================================================================
# Gradle Build
#==============================================================================
echo "==> Building JAR artifacts..."
cd "${PROJECT_ROOT}"
./gradlew clean :imagekit-backend-server:bootJar :imagebuild-controller:bootJar -x test -x asciidoctor
echo "==> Gradle build complete"

#==============================================================================
# Docker Build — imagekit-backend-server
#==============================================================================
echo ""
echo "==> Building image: ${BACKEND_IMAGE}"
docker build \
  --platform "${PLATFORM}" \
  -t "${BACKEND_IMAGE}" \
  -f "${PROJECT_ROOT}/imagekit-backend-server/Dockerfile" \
  "${PROJECT_ROOT}"

#==============================================================================
# Docker Build — imagebuild-controller
#==============================================================================
echo ""
echo "==> Building image: ${CONTROLLER_IMAGE}"
docker build \
  --platform "${PLATFORM}" \
  -t "${CONTROLLER_IMAGE}" \
  -f "${PROJECT_ROOT}/imagebuild-controller/Dockerfile" \
  "${PROJECT_ROOT}"

echo ""
echo "==> Build complete"
echo "    ${BACKEND_IMAGE}"
echo "    ${CONTROLLER_IMAGE}"

#==============================================================================
# Push
#==============================================================================
if [[ "${PUSH:-false}" == "true" ]]; then
  echo ""
  echo "==> Pushing images..."
  docker push "${BACKEND_IMAGE}"
  docker push "${CONTROLLER_IMAGE}"
  echo "==> Push complete"
fi
