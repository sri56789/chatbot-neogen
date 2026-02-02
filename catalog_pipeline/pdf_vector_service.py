import os
from typing import List, Optional

import faiss
import numpy as np
import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel


class IndexRequest(BaseModel):
    chunks: List[str]
    model: str = "text-embedding-3-small"
    batch_size: int = 128


class QueryRequest(BaseModel):
    query: str
    top_k: int = 5


class PdfVectorService:
    def __init__(self):
        self.index = None
        self.chunks: List[str] = []
        self.embedding_model = "text-embedding-3-small"

    def _api_key(self) -> str:
        return (
            os.getenv("EMBEDDING_API_KEY")
            or os.getenv("OPENAI_API_KEY")
            or os.getenv("LLM_API_KEY")
            or ""
        )

    def _embed_texts(self, texts: List[str], model: str, batch_size: int) -> np.ndarray:
        api_key = self._api_key()
        if not api_key:
            raise RuntimeError("Missing EMBEDDING_API_KEY or OPENAI_API_KEY")
        url = "https://api.openai.com/v1/embeddings"
        headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}

        vectors: List[np.ndarray] = []
        for start in range(0, len(texts), batch_size):
            batch = texts[start:start + batch_size]
            payload = {"model": model, "input": batch}
            resp = requests.post(url, headers=headers, json=payload, timeout=120)
            resp.raise_for_status()
            data = resp.json()["data"]
            data = sorted(data, key=lambda x: x["index"])
            for item in data:
                vectors.append(np.array(item["embedding"], dtype="float32"))

        if not vectors:
            return np.empty((0, 0), dtype="float32")

        matrix = np.vstack(vectors)
        faiss.normalize_L2(matrix)
        return matrix

    def index_chunks(self, chunks: List[str], model: str, batch_size: int) -> None:
        if not chunks:
            self.index = None
            self.chunks = []
            return
        matrix = self._embed_texts(chunks, model, batch_size)
        if matrix.size == 0:
            raise RuntimeError("Embedding returned empty vectors")
        dim = matrix.shape[1]
        index = faiss.IndexFlatIP(dim)
        index.add(matrix)
        self.index = index
        self.chunks = chunks
        self.embedding_model = model

    def query(self, query: str, top_k: int) -> (List[str], List[float]):
        if self.index is None or not self.chunks:
            return [], []
        vec = self._embed_texts([query], self.embedding_model, 1)
        if vec.size == 0:
            return [], []
        scores, indices = self.index.search(vec, top_k)
        documents = []
        score_list = []
        for score, idx in zip(scores[0], indices[0]):
            if idx < 0 or idx >= len(self.chunks):
                continue
            documents.append(self.chunks[idx])
            score_list.append(float(score))
        return documents, score_list


app = FastAPI()
SERVICE = PdfVectorService()


@app.get("/status")
def status():
    return {
        "status": "ok",
        "ready": SERVICE.index is not None,
        "chunks": len(SERVICE.chunks),
        "model": SERVICE.embedding_model,
    }


@app.post("/index")
def index(req: IndexRequest):
    try:
        SERVICE.index_chunks(req.chunks, req.model, req.batch_size)
        return {"status": "ok", "chunks": len(SERVICE.chunks)}
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/query")
def query(req: QueryRequest):
    try:
        documents, scores = SERVICE.query(req.query, req.top_k)
        return {"documents": documents, "scores": scores}
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
