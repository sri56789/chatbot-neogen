import argparse
import base64
import json
import os
from pathlib import Path
from typing import Any, Dict, List, Union

import time
import requests


SYSTEM_PROMPT = (
    "You extract product data from blinds catalog images. "
    "Return JSON only. Each product must include: "
    "product_name, model_number, dimensions, materials, colors, mount_type, pricing, notes. "
    "If a field is missing, use null."
)

def parse_json_safely(content: str) -> Union[Dict[str, Any], List[Dict[str, Any]]]:
    if not content:
        raise ValueError("Empty response content")
    content = content.strip()
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        pass

    # Try to extract JSON object or array from a larger response
    obj_start = content.find("{")
    obj_end = content.rfind("}")
    arr_start = content.find("[")
    arr_end = content.rfind("]")

    candidates = []
    if obj_start != -1 and obj_end != -1 and obj_end > obj_start:
        candidates.append(content[obj_start:obj_end + 1])
    if arr_start != -1 and arr_end != -1 and arr_end > arr_start:
        candidates.append(content[arr_start:arr_end + 1])

    for candidate in candidates:
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            continue

    raise ValueError("Failed to parse JSON from response")

def encode_image(image_path: Path) -> str:
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


def call_vision(api_key: str, model: str, image_b64: str) -> Dict[str, Any]:
    url = "https://api.openai.com/v1/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}

    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "Extract product data from this catalog page image."},
                    {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{image_b64}"}},
                ],
            },
        ],
        "temperature": 0,
        "max_tokens": 1200,
    }

    max_retries = 5
    backoff = 2
    for attempt in range(1, max_retries + 1):
        resp = requests.post(url, headers=headers, json=payload, timeout=60)
        if resp.status_code == 429:
            retry_after = resp.headers.get("Retry-After")
            sleep_seconds = int(retry_after) if retry_after and retry_after.isdigit() else backoff
            time.sleep(sleep_seconds)
            backoff = min(backoff * 2, 30)
            continue
        resp.raise_for_status()
        data = resp.json()
        content = data["choices"][0]["message"]["content"]
        return parse_json_safely(content)
    raise RuntimeError("Vision API rate limited after multiple retries")


def normalize_products(raw: Dict[str, Any]) -> List[Dict[str, Any]]:
    if not raw:
        return []
    if isinstance(raw, dict) and "products" in raw:
        products = raw.get("products") or []
    else:
        products = raw if isinstance(raw, list) else []

    normalized = []
    for p in products:
        normalized.append(
            {
                "product_name": p.get("product_name"),
                "model_number": p.get("model_number"),
                "dimensions": p.get("dimensions"),
                "materials": p.get("materials"),
                "colors": p.get("colors"),
                "mount_type": p.get("mount_type"),
                "pricing": p.get("pricing"),
                "notes": p.get("notes"),
            }
        )
    return normalized


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract structured products from catalog images.")
    parser.add_argument("--images", required=True, help="Directory with page images")
    parser.add_argument("--pdf-name", required=True, help="Source PDF filename")
    parser.add_argument("--out", default="catalog_products.json", help="Output JSON path")
    parser.add_argument("--model", default=os.getenv("VISION_MODEL", "gpt-4o-mini"))
    args = parser.parse_args()

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("Missing OPENAI_API_KEY environment variable")

    images_dir = Path(args.images)
    if not images_dir.exists():
        raise FileNotFoundError(f"Images dir not found: {images_dir}")

    output: List[Dict[str, Any]] = []
    for image_path in sorted(images_dir.glob("*.png")):
        page_number = int(image_path.stem.split("_page_")[-1])
        image_b64 = encode_image(image_path)
        print(f"Processing {image_path.name}...", flush=True)
        try:
            raw = call_vision(api_key, args.model, image_b64)
        except Exception as exc:
            print(f"Warning: failed to process {image_path.name}: {exc}")
            continue
        products = normalize_products(raw)

        for product in products:
            product.update(
                {
                    "source_pdf": args.pdf_name,
                    "source_page": page_number,
                    "image_path": image_path.as_posix(),
                }
            )
            output.append(product)
        print(f"✓ Extracted {len(products)} products from {image_path.name}", flush=True)

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2)

    print(f"Wrote {len(output)} products to {args.out}")


if __name__ == "__main__":
    main()
