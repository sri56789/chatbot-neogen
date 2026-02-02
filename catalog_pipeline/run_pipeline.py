import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from pathlib import Path


def run(cmd: list[str], cwd: Path) -> None:
    result = subprocess.run(cmd, check=False, cwd=cwd.as_posix())
    if result.returncode != 0:
        raise RuntimeError(f"Command failed: {' '.join(cmd)}")


def main() -> None:
    parser = argparse.ArgumentParser(description="End-to-end catalog pipeline.")
    parser.add_argument("--pdf-dir", default="pdfs", help="Directory with catalog PDFs")
    parser.add_argument("--images-dir", default="catalog_images", help="Output images root")
    parser.add_argument("--products", default="catalog_products.json", help="Output products JSON")
    parser.add_argument("--index-dir", default="vector_index", help="Output FAISS index dir")
    args = parser.parse_args()

    pipeline_root = Path(__file__).resolve().parent
    pdf_dir = Path(args.pdf_dir)
    if not pdf_dir.exists():
        raise FileNotFoundError(f"PDF directory not found: {pdf_dir}")

    pdfs = sorted(pdf_dir.glob("*.pdf"))
    if not pdfs:
        raise RuntimeError(f"No PDFs found in {pdf_dir}")

    images_root = Path(args.images_dir)
    products_out = Path(args.products)

    all_products = []
    python_exec = sys.executable or "python3"
    for pdf in pdfs:
        pdf_images_dir = images_root / pdf.stem
        run(
            [
                python_exec,
                (pipeline_root / "extract_images.py").as_posix(),
                str(pdf),
                "--out",
                str(pdf_images_dir),
            ],
            cwd=pipeline_root,
        )

        per_pdf_products = products_out.with_name(f"{pdf.stem}_products.json")
        run(
            [
                python_exec,
                (pipeline_root / "extract_products.py").as_posix(),
                "--images",
                str(pdf_images_dir),
                "--pdf-name",
                pdf.name,
                "--out",
                str(per_pdf_products),
            ],
            cwd=pipeline_root,
        )

        with open(per_pdf_products, "r", encoding="utf-8") as f:
            products = json.load(f)
            all_products.extend(products)

    with open(products_out, "w", encoding="utf-8") as f:
        json.dump(all_products, f, indent=2)

    run(
        [
            python_exec,
            (pipeline_root / "build_index.py").as_posix(),
            "--products",
            str(products_out),
            "--out-dir",
            args.index_dir,
        ],
        cwd=pipeline_root,
    )

    print(f"Pipeline complete. Products: {len(all_products)}. Index: {args.index_dir}")


if __name__ == "__main__":
    main()
