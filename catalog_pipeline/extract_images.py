import argparse
import os
from pathlib import Path

import fitz  # PyMuPDF


def extract_images(pdf_path: Path, output_dir: Path, dpi: int) -> None:
    doc = fitz.open(pdf_path)
    output_dir.mkdir(parents=True, exist_ok=True)

    for page_index in range(len(doc)):
        page = doc.load_page(page_index)
        matrix = fitz.Matrix(dpi / 72, dpi / 72)
        pix = page.get_pixmap(matrix=matrix)
        output_path = output_dir / f"{pdf_path.stem}_page_{page_index + 1}.png"
        pix.save(output_path.as_posix())

    doc.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract PDF pages as images.")
    parser.add_argument("pdf", help="Path to PDF file")
    parser.add_argument("--out", default="output_images", help="Output directory")
    parser.add_argument("--dpi", type=int, default=200, help="Render DPI (default 200)")
    args = parser.parse_args()

    pdf_path = Path(args.pdf)
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    output_dir = Path(args.out)
    extract_images(pdf_path, output_dir, args.dpi)
    print(f"Extracted images to: {output_dir.resolve()}")


if __name__ == "__main__":
    main()
