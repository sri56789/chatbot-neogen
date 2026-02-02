# How to Upload PDFs to Your Cloud Run Application

Since your application is deployed on Cloud Run, containers are **ephemeral**. PDFs need to be included in the Docker image at build time.

## Quick Setup

### Step 1: Add PDFs Locally

Place your PDF files in the `pdfs/` folder in your project:

```bash
# From project root
cp /path/to/your/document.pdf pdfs/
# Or copy multiple PDFs
cp *.pdf pdfs/
```

### Step 2: Rebuild and Redeploy

Rebuild the Docker image (PDFs will be included) and redeploy:

```bash
export GCP_PROJECT_ID=big-calling-483404-r0
export REGION=us-central1
export LLM_API_KEY=your-api-key  # Optional

# Rebuild and deploy (PDFs are included in the image)
./deploy-quick.sh
```

Or manually:

```bash
PROJECT_ID=big-calling-483404-r0
REGION=us-central1

# Build Docker image (includes PDFs from pdfs/ folder)
gcloud builds submit --tag ${REGION}-docker.pkg.dev/$PROJECT_ID/docker-repo/pdf-chatbot:latest .

# Deploy to Cloud Run
gcloud run deploy pdf-chatbot \
  --image ${REGION}-docker.pkg.dev/$PROJECT_ID/docker-repo/pdf-chatbot:latest \
  --region $REGION \
  --memory 2Gi \
  --cpu 2 \
  --timeout 300 \
  --max-instances 10 \
  --set-env-vars LLM_API_KEY=$LLM_API_KEY
```

### Step 3: Reload PDFs in UI

1. Open your app: https://pdf-chatbot-peyppm5ttq-uc.a.run.app
2. Click "Reload PDFs" button
3. PDFs will be loaded from the container

## Workflow

```bash
# 1. Add new PDFs
cp new-document.pdf pdfs/

# 2. Deploy (rebuilds with new PDFs)
export GCP_PROJECT_ID=big-calling-483404-r0
./deploy-quick.sh

# 3. Reload PDFs in the UI
# Visit your app and click "Reload PDFs"
```

## Important Notes

⚠️ **Important**: Each time you add or change PDFs, you need to:
1. Place PDFs in the `pdfs/` folder locally
2. Rebuild the Docker image
3. Redeploy to Cloud Run
4. Click "Reload PDFs" in the UI

📦 **PDFs are embedded in the container** - They're part of the Docker image, so they persist across container restarts.

🚀 **For production with frequent PDF updates**, consider using Cloud Storage (requires code changes).

## Verification

Check that PDFs are loaded:

```bash
# View Cloud Run logs
gcloud run services logs read pdf-chatbot --region us-central1

# Look for:
# "PDF directory found at: /app/pdfs"
# "Found X PDF file(s) in directory"
```

## Example Workflow

```bash
# 1. Add PDFs
cp ~/Documents/important-doc.pdf pdfs/
cp ~/Documents/another-doc.pdf pdfs/

# 2. Deploy (includes PDFs)
export GCP_PROJECT_ID=big-calling-483404-r0
./deploy-quick.sh

# 3. Wait for deployment to complete

# 4. Open app and click "Reload PDFs"
# https://pdf-chatbot-peyppm5ttq-uc.a.run.app
```

## Troubleshooting

### PDFs not showing up?

1. **Check PDFs are in the pdfs/ folder** before building
   ```bash
   ls -la pdfs/*.pdf
   ```

2. **Verify PDFs are copied in Dockerfile**
   - Check Dockerfile has: `COPY pdfs/ /app/pdfs/`

3. **Check container logs** for PDF directory location:
   ```bash
   gcloud run services logs read pdf-chatbot --region us-central1 | grep -i pdf
   ```

4. **Ensure file extensions are `.pdf`** (lowercase)

5. **Click "Reload PDFs"** button after deployment

### Updating PDFs

To update PDFs:
1. Replace PDFs in the `pdfs/` folder
2. Rebuild and redeploy: `./deploy-quick.sh`
3. Click "Reload PDFs" in the UI
