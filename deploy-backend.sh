#!/bin/bash
set -e

./gradlew :backend-server:bootBuildImage --imageName=aipub-brewery-backend:0.0.1
docker save -o server.tar aipub-brewery-backend:0.0.1
sudo ctr -n k8s.io images import server.tar
sudo kubectl rollout restart deploy -n aipub aipub-brewery-backend
rm -f server.tar

echo "Backend server deployed successfully."
