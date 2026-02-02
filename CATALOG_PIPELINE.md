# Blinds Catalog Pipeline

This pipeline converts image-based PDF catalogs into structured product data and a FAISS vector index.

## 1) Install Python dependencies

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r catalog_pipeline/requirements.txt
```

## 2) Run the full pipeline (recommended)

```bash
export OPENAI_API_KEY=your-key
python catalog_pipeline/run_pipeline.py --pdf-dir pdfs
```

## 3) Extract page images from the PDF

```bash
python catalog_pipeline/extract_images.py pdfs/your_catalog.pdf --out catalog_images
```

## 4) Extract structured products using vision

```bash
export OPENAI_API_KEY=your-key
python catalog_pipeline/extract_products.py \
  --images catalog_images \
  --pdf-name your_catalog.pdf \
  --out catalog_products.json
```

## 5) Build FAISS index

```bash
export OPENAI_API_KEY=your-key
python catalog_pipeline/build_index.py \
  --products catalog_products.json \
  --out-dir vector_index
```

## 6) Start the vector service

```bash
export OPENAI_API_KEY=your-key
export CATALOG_INDEX_DIR=vector_index
uvicorn catalog_pipeline.vector_service:app --host 0.0.0.0 --port 9000
```

## 7) Enable catalog mode in the backend

Set these values in `backend/src/main/resources/application.properties`:

```
catalog.enabled=true
catalog.vector.url=http://localhost:9000
catalog.vector.topK=5
catalog.confidence.minScore=0.2
```

Then run:

```bash
./build-and-run.sh
```
