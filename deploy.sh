#!/bin/bash
set -e

echo "=========================================="
echo "Deploying to Google Cloud Run"
echo "=========================================="

# Get project ID - support both env var and interactive
if [ -z "$PROJECT_ID" ]; then
  PROJECT_ID=${GCP_PROJECT_ID:-""}
fi

if [ -z "$PROJECT_ID" ]; then
  CURRENT_PROJECT=$(gcloud config get-value project 2>/dev/null)
  if [ -z "$CURRENT_PROJECT" ]; then
    read -p "Enter your GCP Project ID: " PROJECT_ID
  else
    echo "Using current project: $CURRENT_PROJECT"
    read -p "Press Enter to continue or type a different Project ID: " INPUT
    PROJECT_ID=${INPUT:-$CURRENT_PROJECT}
  fi
fi

export REGION=${REGION:-${GCP_REGION:-us-central1}}
export LLM_API_KEY=${LLM_API_KEY:-""}
USE_CLOUD_BUILD=${USE_CLOUD_BUILD:-"true"}

echo ""
echo "Configuration:"
echo "  Project ID: $PROJECT_ID"
echo "  Region: $REGION"
echo "  LLM API Key: ${LLM_API_KEY:+***SET***} ${LLM_API_KEY:-NOT SET (will use fallback)}"
echo "  Using Cloud Build: $USE_CLOUD_BUILD (recommended)"
echo ""

# Set project
gcloud config set project $PROJECT_ID

# Enable APIs
echo "Step 1: Enabling required APIs..."
gcloud services enable run.googleapis.com --quiet
gcloud services enable cloudbuild.googleapis.com --quiet
gcloud services enable artifactregistry.googleapis.com --quiet
gcloud services enable containerregistry.googleapis.com --quiet 2>/dev/null || true

# Method 1: Use Cloud Build (Recommended - handles everything automatically)
if [ "$USE_CLOUD_BUILD" = "true" ]; then
  echo ""
  echo "Step 2: Setting up Artifact Registry..."
  REPO_LOCATION="${REGION}"
  REPO_NAME="docker-repo"
  
  # Check if repository exists, create if not
  if gcloud artifacts repositories describe $REPO_NAME \
      --location=$REPO_LOCATION \
      --repository-format=docker &>/dev/null; then
    echo "  ✓ Artifact Registry repository already exists"
  else
    echo "Creating Artifact Registry repository..."
    # Try to create, ignore "already exists" error
    CREATE_OUTPUT=$(gcloud artifacts repositories create $REPO_NAME \
        --repository-format=docker \
        --location=$REPO_LOCATION \
        --description="Docker repository for PDF Chatbot" \
        2>&1) || true
    
    # Check if creation succeeded or if it already exists
    if echo "$CREATE_OUTPUT" | grep -qi "ALREADY_EXISTS"; then
      echo "  ✓ Repository already exists"
    elif echo "$CREATE_OUTPUT" | grep -qi "created"; then
      echo "  ✓ Artifact Registry repository created"
    else
      # Verify it exists now (may have been created concurrently)
      if gcloud artifacts repositories describe $REPO_NAME \
          --location=$REPO_LOCATION \
          --repository-format=docker &>/dev/null; then
        echo "  ✓ Repository exists"
      else
        echo "  ERROR: Failed to create Artifact Registry repository"
        echo "  $CREATE_OUTPUT"
        echo "  Please check you have proper permissions or try:"
        echo "    gcloud artifacts repositories create $REPO_NAME --repository-format=docker --location=$REPO_LOCATION"
        exit 1
      fi
    fi
  fi
  
  # Build and push using Cloud Build (most reliable in Cloud Shell)
  echo ""
  echo "Step 3: Building and pushing image using Cloud Build..."
  echo "  (This may take 5-10 minutes on first build)"
  
  ARTIFACT_IMAGE="${REPO_LOCATION}-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/pdf-chatbot:latest"
  
  if gcloud builds submit --tag "$ARTIFACT_IMAGE" . 2>&1; then
    IMAGE_NAME="$ARTIFACT_IMAGE"
    echo "  ✓ Image built and pushed successfully using Cloud Build"
  else
    echo ""
    echo "  Cloud Build failed, trying Container Registry with auto-push..."
    # Fallback: Use Container Registry with Cloud Build
    GCR_IMAGE="gcr.io/$PROJECT_ID/pdf-chatbot:latest"
    if gcloud builds submit --tag "$GCR_IMAGE" . 2>&1; then
      IMAGE_NAME="$GCR_IMAGE"
      echo "  ✓ Image built and pushed to Container Registry"
    else
      echo ""
      echo "=========================================="
      echo "ERROR: Cloud Build failed for both registries"
      echo "=========================================="
      echo ""
      echo "Common fixes:"
      echo "  1. Grant Cloud Build service account permissions:"
      echo "     PROJECT_NUMBER=\$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')"
      echo "     gcloud projects add-iam-policy-binding $PROJECT_ID \\"
      echo "       --member='serviceAccount:\${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com' \\"
      echo "       --role='roles/run.admin'"
      echo ""
      echo "  2. Check billing is enabled:"
      echo "     gcloud billing projects describe $PROJECT_ID"
      echo ""
      echo "  3. Try manual Docker build (if docker is available):"
      echo "     export USE_CLOUD_BUILD=false"
      echo "     ./deploy.sh"
      exit 1
    fi
  fi
else
  # Method 2: Use Docker directly (requires Docker and manual push)
  echo ""
  echo "Step 2: Setting up registry..."
  REPO_LOCATION="${REGION}"
  REPO_NAME="docker-repo"
  
  # Try Artifact Registry first
  if gcloud artifacts repositories describe $REPO_NAME \
      --location=$REPO_LOCATION \
      --repository-format=docker &>/dev/null 2>&1; then
    echo "  ✓ Artifact Registry repository found"
    ARTIFACT_IMAGE="${REPO_LOCATION}-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/pdf-chatbot:latest"
    gcloud auth configure-docker ${REPO_LOCATION}-docker.pkg.dev --quiet
    IMAGE_NAME="$ARTIFACT_IMAGE"
  else
    echo "  Using Container Registry (gcr.io)"
    gcloud auth configure-docker gcr.io --quiet
    IMAGE_NAME="gcr.io/$PROJECT_ID/pdf-chatbot:latest"
  fi
  
  echo ""
  echo "Step 3: Building Docker image..."
  echo "  (This may take 5-10 minutes on first build)"
  docker build -t "$IMAGE_NAME" .
  
  echo ""
  echo "Step 4: Pushing image..."
  if ! docker push "$IMAGE_NAME" 2>&1; then
    echo ""
    echo "=========================================="
    echo "ERROR: Failed to push image"
    echo "=========================================="
    echo ""
    echo "Try using Cloud Build instead (recommended):"
    echo "  export USE_CLOUD_BUILD=true"
    echo "  ./deploy.sh"
    exit 1
  fi
  echo "  ✓ Image pushed successfully"
fi

# Deploy to Cloud Run
echo ""
echo "Step 4: Deploying to Cloud Run..."
echo "  Using image: $IMAGE_NAME"

DEPLOY_ARGS=(
  "gcloud" "run" "deploy" "pdf-chatbot"
  "--image" "$IMAGE_NAME"
  "--region" "$REGION"
  "--platform" "managed"
  "--allow-unauthenticated"
  "--memory" "2Gi"
  "--cpu" "2"
  "--timeout" "300"
  "--max-instances" "10"
)

if [ -n "$LLM_API_KEY" ]; then
  DEPLOY_ARGS+=("--set-env-vars" "LLM_API_KEY=$LLM_API_KEY")
fi

"${DEPLOY_ARGS[@]}"

# Get URL
APP_URL=$(gcloud run services describe pdf-chatbot --region $REGION --format 'value(status.url)')

echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Application URL: $APP_URL"
echo "Image: $IMAGE_NAME"
echo ""
echo "Your single Spring Boot app is now live!"
echo "It serves both UI and API from one endpoint."
echo ""
echo "Next steps:"
echo "  1. Open the URL in your browser: $APP_URL"
echo "  2. Click 'Reload PDFs' after uploading PDFs to the pdfs/ folder"
echo "  3. Start asking questions!"
echo ""
echo "To view logs:"
echo "  gcloud run services logs read pdf-chatbot --region $REGION"
echo "=========================================="
