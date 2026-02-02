import json
import os
from pathlib import Path
from typing import List

import faiss
import numpy as np
import requests
from fastapi import FastAPI
from pydantic import BaseModel


class QueryRequest(BaseModel):
    query: str
    top_k: int = 5


class VectorService:
    def __init__(self, index_dir: Path):
        self.index_dir = index_dir
        self.index = None
        self.metadata = {}
        self.products = []
        self.embedding_model = "text-embedding-3-small"
        self.last_error = None
        self._load_if_available()

    def _load_if_available(self) -> None:
        index_path = self.index_dir / "catalog.index"
        meta_path = self.index_dir / "metadata.json"
        if not index_path.exists() or not meta_path.exists():
            self.last_error = "Catalog index not found. Run the pipeline to build it."
            return
        try:
            self.index = faiss.read_index(str(index_path))
            with open(meta_path, "r", encoding="utf-8") as f:
                self.metadata = json.load(f)
            self.products = self.metadata.get("products", [])
            self.embedding_model = self.metadata.get("model", "text-embedding-3-small")
            self.last_error = None
        except Exception as exc:
            self.last_error = str(exc)

    def embed_query(self, query: str) -> np.ndarray:
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise RuntimeError("Missing OPENAI_API_KEY environment variable")
        url = "https://api.openai.com/v1/embeddings"
        headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
        payload = {"model": self.embedding_model, "input": [query]}
        resp = requests.post(url, headers=headers, json=payload, timeout=60)
        resp.raise_for_status()
        vector = resp.json()["data"][0]["embedding"]
        vec = np.array(vector, dtype="float32").reshape(1, -1)
        faiss.normalize_L2(vec)
        return vec

    def query(self, query: str, top_k: int) -> List[dict]:
        if self.index is None:
            return []
        vec = self.embed_query(query)
        scores, indices = self.index.search(vec, top_k)
        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx < 0 or idx >= len(self.products):
                continue
            results.append(
                {
                    "score": float(score),
                    "product": self.products[idx],
                }
            )
        return results


app = FastAPI()
INDEX_DIR = Path(os.getenv("CATALOG_INDEX_DIR", "vector_index"))
SERVICE = VectorService(INDEX_DIR)


@app.get("/status")
def status():
    return {
        "status": "ok",
        "ready": SERVICE.index is not None,
        "products": len(SERVICE.products),
        "error": SERVICE.last_error,
    }


@app.post("/query")
def query(req: QueryRequest):
    if SERVICE.index is None:
        return {"results": [], "error": SERVICE.last_error or "Catalog index not ready"}
    results = SERVICE.query(req.query, req.top_k)
    return {"results": results}
