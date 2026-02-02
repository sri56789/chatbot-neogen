#!/bin/bash
set -e

echo "=========================================="
echo "Building Single Application"
echo "=========================================="

# Step 1: Build frontend
echo ""
echo "Step 1: Building Next.js frontend..."
cd frontend

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js is not installed. Please install Node.js from https://nodejs.org/"
    exit 1
fi

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo "ERROR: npm is not installed. Please install npm."
    exit 1
fi

# Install dependencies if needed
if [ ! -d "node_modules" ] || [ ! -f "node_modules/.bin/next" ]; then
    echo "Installing npm dependencies..."
    npm install
    if [ $? -ne 0 ]; then
        echo "ERROR: npm install failed!"
        exit 1
    fi
fi

echo "Building frontend (this may take a minute)..."
npm run build

if [ ! -d "out" ]; then
    echo "ERROR: Frontend build failed! 'out' directory not found."
    exit 1
fi

echo "✓ Frontend built successfully"

# Step 2: Copy to backend resources
echo ""
echo "Step 2: Copying frontend to backend resources..."
cd ..
mkdir -p backend/src/main/resources/static

# Clean previous build
rm -rf backend/src/main/resources/static/*

# Copy new build
cp -r frontend/out/* backend/src/main/resources/static/

echo "✓ Frontend files copied to backend"

# Step 3: Build Spring Boot
echo ""
echo "Step 3: Building Spring Boot application..."
cd backend
mvn clean package -DskipTests

if [ ! -f "target/pdf-chatbot-0.0.1.jar" ]; then
    echo "ERROR: Spring Boot build failed!"
    exit 1
fi

echo "✓ Spring Boot built successfully"

echo ""
echo "Step 4: FAISS vector services"
APP_ROOT=$(cd .. && pwd)

if ! command -v python3 &> /dev/null; then
    echo "ERROR: python3 is not installed."
    exit 1
fi

PY_VENV_DIR="${APP_ROOT}/catalog_pipeline/.venv"
PY_BIN="${PY_VENV_DIR}/bin"
PYTHON="${PY_BIN}/python"
PIP="${PY_BIN}/pip"

if [ ! -d "${PY_VENV_DIR}" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv "${PY_VENV_DIR}"
fi

if [ ! -f "${PIP}" ]; then
    echo "ERROR: pip is not available in the virtual environment."
    exit 1
fi

echo "Installing Python dependencies..."
"${PIP}" install -r "${APP_ROOT}/catalog_pipeline/requirements.txt"

echo "Starting PDF FAISS vector service..."
PDF_VECTOR_PORT=${PDF_VECTOR_PORT:-9100}
SERVICE_READY=$("${PYTHON}" - <<PY
import urllib.request

url = "http://localhost:${PDF_VECTOR_PORT}/status"
try:
    with urllib.request.urlopen(url, timeout=2) as resp:
        if resp.status == 200:
            print("ready")
except Exception:
    pass
PY
)

if [ "$SERVICE_READY" = "ready" ]; then
    echo "✓ PDF vector service already running on port ${PDF_VECTOR_PORT}"
else
    "${PY_BIN}/uvicorn" catalog_pipeline.pdf_vector_service:app --app-dir "${APP_ROOT}" --host 0.0.0.0 --port ${PDF_VECTOR_PORT} --access-log false &
    PDF_VECTOR_PID=$!
    echo "✓ PDF vector service started on port ${PDF_VECTOR_PORT} (pid ${PDF_VECTOR_PID})"

    echo "Waiting for PDF vector service to be ready..."
    "${PYTHON}" - <<PY
import time
import urllib.request

url = "http://localhost:${PDF_VECTOR_PORT}/status"
for i in range(30):
    try:
        with urllib.request.urlopen(url, timeout=2) as resp:
            if resp.status == 200:
                print("✓ PDF vector service is ready")
                raise SystemExit(0)
    except Exception:
        time.sleep(1)
raise SystemExit("ERROR: PDF vector service did not become ready in time")
PY
fi

echo ""
echo "Catalog pipeline (FAISS)"
CATALOG_ENABLED=$(awk -F= '/^catalog.enabled=/ {print $2}' src/main/resources/application.properties | tr -d '\r')
if [ "$CATALOG_ENABLED" = "true" ]; then
    if [ -z "$OPENAI_API_KEY" ]; then
        echo "ERROR: OPENAI_API_KEY is required for catalog pipeline."
        exit 1
    fi
    if [ ! -f "../catalog_pipeline/run_pipeline.py" ]; then
        echo "ERROR: catalog pipeline script not found."
        exit 1
    fi
    echo "Running catalog pipeline to build FAISS index..."
    "${PYTHON}" ../catalog_pipeline/run_pipeline.py --pdf-dir ../pdfs --images-dir ../catalog_images --products ../catalog_products.json --index-dir ../vector_index
    echo "✓ Catalog pipeline completed"

    echo "Starting catalog vector service..."
    CATALOG_VECTOR_PORT=${CATALOG_VECTOR_PORT:-9000}
    export CATALOG_INDEX_DIR=../vector_index
    CATALOG_READY=$("${PYTHON}" - <<PY
import urllib.request

url = "http://localhost:${CATALOG_VECTOR_PORT}/status"
try:
    with urllib.request.urlopen(url, timeout=2) as resp:
        if resp.status == 200:
            print("ready")
except Exception:
    pass
PY
)

    if [ "$CATALOG_READY" = "ready" ]; then
        echo "✓ Catalog vector service already running on port ${CATALOG_VECTOR_PORT}"
    else
        if command -v lsof &> /dev/null; then
            EXISTING_PID=$(lsof -ti tcp:${CATALOG_VECTOR_PORT} || true)
            if [ -n "$EXISTING_PID" ]; then
                echo "Stopping existing catalog vector service (pid ${EXISTING_PID})..."
                kill ${EXISTING_PID} 2>/dev/null || true
            fi
        fi
        "${PY_BIN}/uvicorn" catalog_pipeline.vector_service:app --app-dir "${APP_ROOT}" --host 0.0.0.0 --port ${CATALOG_VECTOR_PORT} --access-log false &
        CATALOG_VECTOR_PID=$!
        echo "✓ Catalog vector service started on port ${CATALOG_VECTOR_PORT} (pid ${CATALOG_VECTOR_PID})"

        echo "Waiting for catalog vector service to be ready..."
        "${PYTHON}" - <<PY
import time
import urllib.request

url = "http://localhost:${CATALOG_VECTOR_PORT}/status"
for i in range(30):
    try:
        with urllib.request.urlopen(url, timeout=2) as resp:
            if resp.status == 200:
                print("✓ Catalog vector service is responding")
                raise SystemExit(0)
    except Exception:
        time.sleep(1)
raise SystemExit("ERROR: Catalog vector service did not become ready in time")
PY
    fi
else
    echo "Catalog pipeline skipped (catalog.enabled=false)"
fi

cleanup() {
    if [ -n "$CATALOG_VECTOR_PID" ]; then
        echo "Stopping catalog vector service (pid ${CATALOG_VECTOR_PID})..."
        kill ${CATALOG_VECTOR_PID} 2>/dev/null || true
    fi
    if [ -n "$PDF_VECTOR_PID" ]; then
        echo "Stopping PDF vector service (pid ${PDF_VECTOR_PID})..."
        kill ${PDF_VECTOR_PID} 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo ""
echo "Step 5: Starting application..."
echo ""
echo "=========================================="
echo "Build Complete!"
echo "=========================================="
echo ""
echo "Starting application..."
echo "Access at: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop"
echo "=========================================="
echo ""

# Run the application
java -jar target/pdf-chatbot-0.0.1.jar


