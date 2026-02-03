#!/bin/sh
set -e

APP_ROOT=/app
PDF_VECTOR_PORT="${PDF_VECTOR_PORT:-9100}"
CATALOG_VECTOR_PORT="${CATALOG_VECTOR_PORT:-9000}"
CATALOG_ENABLED="${CATALOG_ENABLED:-true}"

mkdir -p "${APP_ROOT}/pdfs" "${APP_ROOT}/catalog_images" "${APP_ROOT}/vector_index"

echo "Starting PDF FAISS vector service on port ${PDF_VECTOR_PORT}..."
python3 -m uvicorn catalog_pipeline.pdf_vector_service:app \
  --app-dir "${APP_ROOT}" \
  --host 0.0.0.0 \
  --port "${PDF_VECTOR_PORT}" \
  --access-log false &
PDF_VECTOR_PID=$!

echo "Waiting for PDF vector service..."
python3 - <<PY
import time
import urllib.request

url = "http://localhost:${PDF_VECTOR_PORT}/status"
for _ in range(30):
    try:
        with urllib.request.urlopen(url, timeout=2) as resp:
            if resp.status == 200:
                print("✓ PDF vector service is ready")
                raise SystemExit(0)
    except Exception:
        time.sleep(1)
raise SystemExit("ERROR: PDF vector service did not become ready in time")
PY

if [ "${CATALOG_ENABLED}" = "true" ]; then
  if [ -z "${OPENAI_API_KEY}" ]; then
    echo "ERROR: OPENAI_API_KEY is required for catalog pipeline."
    exit 1
  fi

  echo "Running catalog pipeline to build FAISS index..."
  python3 "${APP_ROOT}/catalog_pipeline/run_pipeline.py" \
    --pdf-dir "${APP_ROOT}/pdfs" \
    --images-dir "${APP_ROOT}/catalog_images" \
    --products "${APP_ROOT}/catalog_products.json" \
    --index-dir "${APP_ROOT}/vector_index"

  export CATALOG_INDEX_DIR="${APP_ROOT}/vector_index"
  echo "Starting catalog vector service on port ${CATALOG_VECTOR_PORT}..."
  python3 -m uvicorn catalog_pipeline.vector_service:app \
    --app-dir "${APP_ROOT}" \
    --host 0.0.0.0 \
    --port "${CATALOG_VECTOR_PORT}" \
    --access-log false &
  CATALOG_VECTOR_PID=$!
fi

cleanup() {
  if [ -n "${CATALOG_VECTOR_PID:-}" ]; then
    kill "${CATALOG_VECTOR_PID}" 2>/dev/null || true
  fi
  if [ -n "${PDF_VECTOR_PID:-}" ]; then
    kill "${PDF_VECTOR_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "Starting Spring Boot app..."
exec sh -c "java ${JAVA_OPTS:-} -jar ${APP_ROOT}/app.jar"
