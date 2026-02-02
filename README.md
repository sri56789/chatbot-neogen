# PDF Chatbot Starter

A full-stack application that allows you to chat with your PDF documents. Ask questions about the content in your PDFs and get AI-powered answers.

## Quick Start

### Local Development

1. **Build and Run:**
   ```bash
   ./build-and-run.sh
   ```
   This builds the frontend, embeds it in the backend, and starts the app on `http://localhost:8080`

2. **Configure LLM (Optional):**
   - Get your OpenAI API key from: https://platform.openai.com/api-keys
   - Set environment variable: `export LLM_API_KEY=your-key-here`
   - Or edit `backend/src/main/resources/application.properties`
   - See [LLM_SETUP.md](./LLM_SETUP.md) for details

3. **Add PDFs:**
   - Place PDF files in the `pdfs/` folder
   - Click "Reload PDFs" in the UI

### Manual Build Steps

```bash
# 1. Build frontend
cd frontend
npm install
npm run build

# 2. Copy to backend resources
cd ..
mkdir -p backend/src/main/resources/static
cp -r frontend/out/* backend/src/main/resources/static/

# 3. Build and run backend
cd backend
mvn clean package
java -jar target/pdf-chatbot-0.0.1.jar
```

## Deployment to Google Cloud

See [DEPLOY.md](./DEPLOY.md) for detailed instructions.

**Quick deployment:**
```bash
export GCP_PROJECT_ID=your-project-id
export LLM_API_KEY=your-api-key  # Optional
./deploy.sh
```

This deploys to Cloud Run as a single service.

## Project Structure

```
pdf-chatbot-starter/
├── backend/          # Spring Boot Java backend
│   └── src/main/resources/static/  # Frontend files (generated)
├── frontend/         # Next.js React frontend (source)
├── pdfs/            # Place your PDF files here
├── Dockerfile       # Single multi-stage build
├── build-and-run.sh # Local build & run script
└── deploy.sh        # Google Cloud deployment script
```

## Architecture

**Single Application Approach:**
- Frontend is built as static files and embedded in the Spring Boot JAR
- Everything runs on one server at `http://localhost:8080`
- API endpoints: `/api/*`
- Static files: served from `/` (everything else)
- No CORS issues - same origin for UI and API

## Features

- ✅ PDF text extraction using Apache PDFBox
- ✅ Text chunking for efficient search
- ✅ Similarity-based search through PDF content
- ✅ Modern chat interface UI
- ✅ Real-time question answering
- ✅ Support for multiple PDF files
- ✅ OpenAI integration for intelligent answers

## API Endpoints

- `POST /api/chat` - Send a question and get an answer
  ```json
  {
    "question": "What is the main topic of the document?"
  }
  ```

- `POST /api/reload` - Reload PDF documents from the pdfs folder

- `GET /api/status` - Get application status

## Technologies

**Backend:**
- Spring Boot 3.2.0
- Java 17
- Apache PDFBox 3.0.1 (PDF processing)
- Apache Commons Text (similarity search)
- Spring WebFlux (HTTP client for LLM API)
- OpenAI API (GPT-3.5/GPT-4)

**Frontend:**
- Next.js 14.0
- React 18
- TypeScript

## Configuration

Edit `backend/src/main/resources/application.properties`:

```properties
# OpenAI API Key (optional - will use fallback if not set)
llm.api.key=${LLM_API_KEY:}

# Model to use (gpt-3.5-turbo, gpt-4, gpt-4-turbo)
llm.model=gpt-3.5-turbo

# Enable/disable LLM (false = fallback text extraction)
llm.enabled=true
```

## Documentation

- [DEPLOY.md](./DEPLOY.md) - Google Cloud deployment guide
- [LLM_SETUP.md](./LLM_SETUP.md) - LLM/OpenAI configuration
- [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) - Common issues and solutions

## Notes

- PDFs are loaded when you click "Reload PDFs" (not on startup)
- Large PDFs may take time to process
- The search uses keyword matching and cosine similarity
- LLM generates contextual answers based on relevant PDF chunks
- If LLM is disabled or not configured, the system falls back to text extraction

## Blinds Catalog (Image PDFs)

For image-based blinds catalogs, use the OCR + vision pipeline and FAISS vector service:

- See `CATALOG_PIPELINE.md` for extraction, indexing, and service steps
- Enable `catalog.enabled=true` in `backend/src/main/resources/application.properties`
 - When `catalog.enabled=true`, `./build-and-run.sh` will build the FAISS index from PDFs in `pdfs/`
