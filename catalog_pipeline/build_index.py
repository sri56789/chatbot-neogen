import argparse
import json
import os
from pathlib import Path
from typing import List

import faiss
import numpy as np
import requests


def embed_texts(texts: List[str], api_key: str, model: str) -> List[List[float]]:
    url = "https://api.openai.com/v1/embeddings"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload = {"model": model, "input": texts}
    resp = requests.post(url, headers=headers, json=payload, timeout=60)
    resp.raise_for_status()
    data = resp.json()
    return [item["embedding"] for item in data["data"]]


def product_to_text(product: dict) -> str:
    fields = [
        ("Product Name", product.get("product_name")),
        ("Model Number", product.get("model_number")),
        ("Dimensions", product.get("dimensions")),
        ("Materials", product.get("materials")),
        ("Colors", product.get("colors")),
        ("Mount Type", product.get("mount_type")),
        ("Pricing", product.get("pricing")),
        ("Notes", product.get("notes")),
    ]
    parts = [f"{label}: {value}" for label, value in fields if value]
    return " | ".join(parts)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build FAISS index from catalog products.")
    parser.add_argument("--products", required=True, help="Path to catalog_products.json")
    parser.add_argument("--out-dir", default="vector_index", help="Output directory")
    parser.add_argument("--model", default=os.getenv("EMBEDDING_MODEL", "text-embedding-3-small"))
    args = parser.parse_args()

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("Missing OPENAI_API_KEY environment variable")

    with open(args.products, "r", encoding="utf-8") as f:
        products = json.load(f)

    texts = [product_to_text(p) for p in products]
    embeddings = embed_texts(texts, api_key, args.model)

    vectors = np.array(embeddings, dtype="float32")
    faiss.normalize_L2(vectors)
    index = faiss.IndexFlatIP(vectors.shape[1])
    index.add(vectors)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    faiss.write_index(index, str(out_dir / "catalog.index"))

    with open(out_dir / "metadata.json", "w", encoding="utf-8") as f:
        json.dump(
            {
                "products": products,
                "model": args.model,
            },
            f,
            indent=2,
        )

    print(f"Indexed {len(products)} products into {out_dir}")


if __name__ == "__main__":
    main()
