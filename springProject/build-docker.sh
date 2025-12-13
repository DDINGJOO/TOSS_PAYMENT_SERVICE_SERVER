#!/bin/bash

set -e

# Configuration
IMAGE_NAME="ddingsh9/toss-server"
IMAGE_TAG="latest"
FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
BUILDER_NAME="multiarch"

echo "=========================================="
echo "  Toss Payment Service Docker Build"
echo "=========================================="
echo "Image: ${FULL_IMAGE}"
echo ""

# Check if buildx is available
if ! docker buildx version > /dev/null 2>&1; then
    echo "Error: Docker buildx is not available"
    exit 1
fi

# Create or use existing builder
echo "[1/4] Setting up buildx builder..."
if ! docker buildx inspect ${BUILDER_NAME} > /dev/null 2>&1; then
    echo "Creating new builder: ${BUILDER_NAME}"
    docker buildx create --name ${BUILDER_NAME} --use
else
    echo "Using existing builder: ${BUILDER_NAME}"
    docker buildx use ${BUILDER_NAME}
fi

# Bootstrap builder
docker buildx inspect --bootstrap > /dev/null 2>&1

# Login check
echo ""
echo "[2/4] Checking Docker Hub login..."
if ! docker info 2>/dev/null | grep -q "Username"; then
    echo "Please login to Docker Hub first: docker login"
    read -p "Continue anyway? (y/N): " confirm
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Build and push
echo ""
echo "[3/4] Building multi-architecture image (amd64 + arm64)..."
echo "This may take several minutes..."
echo ""

docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t ${FULL_IMAGE} \
    --push \
    .

# Verify
echo ""
echo "[4/4] Verifying pushed image..."
docker buildx imagetools inspect ${FULL_IMAGE}

echo ""
echo "=========================================="
echo "  Build Complete!"
echo "=========================================="
echo "Image pushed: ${FULL_IMAGE}"
echo ""
echo "To pull: docker pull ${FULL_IMAGE}"
echo ""
