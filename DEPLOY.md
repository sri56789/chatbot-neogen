# Google Cloud Deployment Guide

Deploy the PDF Chatbot application to Google Cloud Run as a single service.

## Prerequisites

1. **Google Cloud Account**: Sign up at https://cloud.google.com
2. **Google Cloud SDK**: Install from https://cloud.google.com/sdk/docs/install (if deploying locally)
3. **Billing Enabled**: Enable billing on your GCP project

## Quick Deployment

### Using the Deployment Script (Recommended)

```bash
# Set your project ID
export GCP_PROJECT_ID=your-project-id
export LLM_API_KEY=your-api-key  # Optional but recommended

# Deploy
chmod +x deploy.sh
./deploy.sh
```

The script will:
1. ✅ Enable required APIs
2. ✅ Build Docker image (frontend + backend)
3. ✅ Push to Container Registry
4. ✅ Deploy to Cloud Run
5. ✅ Provide you with the live URL

### From Google Cloud Shell

1. Open [Google Cloud Shell](https://shell.cloud.google.com)
2. Upload/clone your code
3. Run:
   ```bash
   export GCP_PROJECT_ID=your-project-id
   export LLM_API_KEY=your-api-key
   chmod +x deploy.sh
   ./deploy.sh
   ```

## Manual Deployment Steps

### 1. Initial Setup (First Time Only)

```bash
# Set your project
export PROJECT_ID=your-project-id
export REGION=us-central1

# Authenticate
gcloud auth login
gcloud config set project $PROJECT_ID

# Enable required APIs
gcloud services enable run.googleapis.com
gcloud services enable containerregistry.googleapis.com

# Configure Docker
gcloud auth configure-docker
```

### 2. Build and Deploy

```bash
# Build Docker image
docker build -t gcr.io/$PROJECT_ID/pdf-chatbot:latest .

# Push to Container Registry
docker push gcr.io/$PROJECT_ID/pdf-chatbot:latest

# Deploy to Cloud Run
gcloud run deploy pdf-chatbot \
  --image gcr.io/$PROJECT_ID/pdf-chatbot:latest \
  --region $REGION \
  --platform managed \
  --allow-unauthenticated \
  --memory 2Gi \
  --cpu 2 \
  --timeout 300 \
  --max-instances 10 \
  --set-env-vars LLM_API_KEY=$LLM_API_KEY
```

### 3. Get Your Application URL

```bash
gcloud run services describe pdf-chatbot \
  --region $REGION \
  --format 'value(status.url)'
```

## Setting Environment Variables

### During Deployment

```bash
gcloud run deploy pdf-chatbot \
  --image gcr.io/$PROJECT_ID/pdf-chatbot:latest \
  --region $REGION \
  --set-env-vars LLM_API_KEY=your-api-key
```

### After Deployment

```bash
gcloud run services update pdf-chatbot \
  --region $REGION \
  --update-env-vars LLM_API_KEY=your-api-key
```

### Using Secret Manager (Recommended for Production)

1. Create a secret:
   ```bash
   echo -n "your-openai-api-key" | gcloud secrets create llm-api-key --data-file=-
   ```

2. Grant Cloud Run access:
   ```bash
   PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
   gcloud secrets add-iam-policy-binding llm-api-key \
     --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
     --role="roles/secretmanager.secretAccessor"
   ```

3. Update the service:
   ```bash
   gcloud run services update pdf-chatbot \
     --region $REGION \
     --update-secrets LLM_API_KEY=llm-api-key:latest
   ```

## Storage for PDFs

### Option 1: Cloud Storage Bucket (Recommended)

1. Create a bucket:
   ```bash
   gsutil mb -l $REGION gs://$PROJECT_ID-pdfs
   ```

2. Upload PDFs:
   ```bash
   gsutil cp pdfs/*.pdf gs://$PROJECT_ID-pdfs/
   ```

3. Update `PdfService.java` to read from Cloud Storage instead of local filesystem.

### Option 2: Persistent Volume (Cloud Run)

Mount a Cloud Filestore instance as a volume in Cloud Run for persistent storage.

## Monitoring and Logging

### View Logs

```bash
gcloud run services logs read pdf-chatbot --region $REGION
```

### View Logs in Console

1. Go to Cloud Run in Google Cloud Console
2. Click on `pdf-chatbot` service
3. Go to "Logs" tab

## Updating the Application

To update after code changes:

```bash
# Rebuild and redeploy
docker build -t gcr.io/$PROJECT_ID/pdf-chatbot:latest .
docker push gcr.io/$PROJECT_ID/pdf-chatbot:latest
gcloud run deploy pdf-chatbot \
  --image gcr.io/$PROJECT_ID/pdf-chatbot:latest \
  --region $REGION
```

Or simply run `./deploy.sh` again.

## Cost Estimation

- **Cloud Run**: Pay per request + compute time
  - ~$0.40 per million requests
  - Compute: ~$0.00002400 per GB-second
  - Free tier: 2 million requests/month, 360,000 GB-seconds

- **Container Registry**: Free for first 5GB storage

- **Typical small usage**: $5-20/month

## Troubleshooting

### Application not starting

- Check logs: `gcloud run services logs read pdf-chatbot --region $REGION`
- Verify memory settings (2Gi minimum)
- Check environment variables are set correctly

### Build errors

- Ensure Docker is running
- Check that all dependencies are available
- Verify Dockerfile is correct

### PDF processing errors

- Increase memory allocation (try 4Gi)
- Check PDF files are valid
- Verify Cloud Storage permissions (if using)

## Cleanup

To delete all resources:

```bash
# Delete Cloud Run service
gcloud run services delete pdf-chatbot --region $REGION

# Delete Docker image
gcloud container images delete gcr.io/$PROJECT_ID/pdf-chatbot:latest

# Delete project (careful!)
gcloud projects delete $PROJECT_ID
```
