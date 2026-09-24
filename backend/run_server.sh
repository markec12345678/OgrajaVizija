#!/bin/bash
# 🟡 Zažene SAMOSTOJEN backend (brez Dockerja, Postgresa, Redisa).
#   pip install -r requirements.txt
#   ./run_server.sh            # CPU / brez difuzije
#   OVIZ_PROVIDER=FLUX2_KLEIN_4B OVIZ_DEVICE=cuda ./run_server.sh   # z GPU-jem
cd "$(dirname "$0")"
exec python3 -m uvicorn app.main:app --host "${OVIZ_HOST:-0.0.0.0}" --port "${OVIZ_PORT:-8787}"
