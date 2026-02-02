#!/bin/bash
# Quick deployment using Cloud Build with Artifact Registry (recommended)
# This avoids the Container Registry permission issues

set -e

PROJECT_ID=${GCP_PROJECT_ID:-"big-calling-483404-r0"}
REGION=${GCP_REGION:-"us-central1"}
LLM_API_KEY=${LLM_API_KEY:-""}

echo "=========================================="
echo "Quick Deploy to Cloud Run"
echo "=========================================="
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo ""

# Set project
gcloud config set project $PROJECT_ID

# Enable APIs
echo "Enabling required APIs..."
gcloud services enable run.googleapis.com --quiet
gcloud services enable cloudbuild.googleapis.com --quiet
gcloud services enable artifactregistry.googleapis.com --quiet

# Setup Artifact Registry
echo ""
echo "Setting up Artifact Registry..."
REPO_NAME="docker-repo"

# Check if repository exists
if gcloud artifacts repositories describe $REPO_NAME \
    --location=$REGION \
    --repository-format=docker &>/dev/null; then
  echo "✓ Repository already exists"
else
  echo "Creating Artifact Registry repository..."
  # Try to create, ignore "already exists" error
  CREATE_OUTPUT=$(gcloud artifacts repositories create $REPO_NAME \
      --repository-format=docker \
      --location=$REGION \
      --description="Docker repository for PDF Chatbot" \
      2>&1) || true
  
  # Check if creation succeeded or if it already exists
  if echo "$CREATE_OUTPUT" | grep -qi "ALREADY_EXISTS"; then
    echo "✓ Repository already exists"
  elif echo "$CREATE_OUTPUT" | grep -qi "created"; then
    echo "✓ Repository created"
  else
    # Verify it exists now (may have been created concurrently)
    if gcloud artifacts repositories describe $REPO_NAME \
        --location=$REGION \
        --repository-format=docker &>/dev/null; then
      echo "✓ Repository exists"
    else
      echo "ERROR: Failed to create repository"
      echo "$CREATE_OUTPUT"
      exit 1
    fi
  fi
fi

# Build and push using Cloud Build (handles everything automatically)
echo ""
echo "Building and pushing image using Cloud Build..."
echo "(This may take 5-10 minutes)"
IMAGE_NAME="${REGION}-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/pdf-chatbot:latest"

if gcloud builds submit --tag "$IMAGE_NAME" .; then
  echo "✓ Image built and pushed successfully"
else
  echo ""
  echo "ERROR: Cloud Build failed"
  echo ""
  echo "Try granting Cloud Build permissions:"
  echo "  PROJECT_NUMBER=\$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')"
  echo "  gcloud projects add-iam-policy-binding $PROJECT_ID \\"
  echo "    --member='serviceAccount:\${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com' \\"
  echo "    --role='roles/run.admin'"
  echo "  gcloud projects add-iam-policy-binding $PROJECT_ID \\"
  echo "    --member='serviceAccount:\${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com' \\"
  echo "    --role='roles/artifactregistry.writer'"
  exit 1
fi

# Deploy to Cloud Run
echo ""
echo "Deploying to Cloud Run..."

DEPLOY_CMD="gcloud run deploy pdf-chatbot \
  --image $IMAGE_NAME \
  --region $REGION \
  --platform managed \
  --allow-unauthenticated \
  --memory 2Gi \
  --cpu 2 \
  --timeout 300 \
  --max-instances 10"

if [ -n "$LLM_API_KEY" ]; then
  DEPLOY_CMD="$DEPLOY_CMD --set-env-vars LLM_API_KEY=$LLM_API_KEY"
fi

eval $DEPLOY_CMD

# Get URL
APP_URL=$(gcloud run services describe pdf-chatbot --region $REGION --format 'value(status.url)')

echo ""
echo "=========================================="
echo "✓ Deployment Complete!"
echo "=========================================="
echo ""
echo "Your app is live at: $APP_URL"
echo ""
echo "Next steps:"
echo "  1. Open: $APP_URL"
echo "  2. Upload PDFs to the pdfs/ folder (or use Cloud Storage)"
echo "  3. Click 'Reload PDFs' in the UI"
echo "  4. Start asking questions!"
echo ""
