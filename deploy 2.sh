#!/bin/bash
set -e

# Configuration
PROJECT_ID=${GCP_PROJECT_ID:-"your-project-id"}
REGION=${GCP_REGION:-"us-central1"}
LLM_API_KEY=${LLM_API_KEY:-""}

if [ "$PROJECT_ID" = "your-project-id" ]; then
  echo "Error: Please set GCP_PROJECT_ID environment variable or edit deploy.sh"
  exit 1
fi

if [ -z "$LLM_API_KEY" ]; then
  echo "Warning: LLM_API_KEY not set. LLM features will be disabled."
fi

echo "Deploying to project: $PROJECT_ID"
echo "Region: $REGION"

# Set project
gcloud config set project $PROJECT_ID

# Enable APIs
echo "Enabling required APIs..."
gcloud services enable run.googleapis.com
gcloud services enable containerregistry.googleapis.com
gcloud services enable cloudbuild.googleapis.com

# Build and deploy backend
echo "Building backend..."
cd backend
docker build -t gcr.io/$PROJECT_ID/pdf-chatbot-backend:latest .
docker push gcr.io/$PROJECT_ID/pdf-chatbot-backend:latest

echo "Deploying backend..."
if [ -z "$LLM_API_KEY" ]; then
  gcloud run deploy pdf-chatbot-backend \
    --image gcr.io/$PROJECT_ID/pdf-chatbot-backend:latest \
    --region $REGION \
    --platform managed \
    --allow-unauthenticated \
    --memory 2Gi \
    --cpu 2 \
    --timeout 300 \
    --max-instances 10
else
  gcloud run deploy pdf-chatbot-backend \
    --image gcr.io/$PROJECT_ID/pdf-chatbot-backend:latest \
    --region $REGION \
    --platform managed \
    --allow-unauthenticated \
    --memory 2Gi \
    --cpu 2 \
    --timeout 300 \
    --max-instances 10 \
    --set-env-vars LLM_API_KEY=$LLM_API_KEY
fi

BACKEND_URL=$(gcloud run services describe pdf-chatbot-backend --region $REGION --format 'value(status.url)')
echo "Backend deployed at: $BACKEND_URL"

# Build and deploy frontend
echo "Building frontend..."
cd ../frontend
docker build \
  --build-arg NEXT_PUBLIC_BACKEND_URL=$BACKEND_URL \
  -t gcr.io/$PROJECT_ID/pdf-chatbot-frontend:latest .

docker push gcr.io/$PROJECT_ID/pdf-chatbot-frontend:latest

echo "Deploying frontend..."
gcloud run deploy pdf-chatbot-frontend \
  --image gcr.io/$PROJECT_ID/pdf-chatbot-frontend:latest \
  --region $REGION \
  --platform managed \
  --allow-unauthenticated \
  --memory 512Mi \
  --timeout 60 \
  --max-instances 10 \
  --set-env-vars NEXT_PUBLIC_BACKEND_URL=$BACKEND_URL

FRONTEND_URL=$(gcloud run services describe pdf-chatbot-frontend --region $REGION --format 'value(status.url)')

echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo "Frontend URL: $FRONTEND_URL"
echo "Backend URL: $BACKEND_URL"
echo ""
echo "Note: PDFs need to be uploaded via the application interface"
echo "or stored in Cloud Storage and accessed via the backend."
echo "=========================================="



