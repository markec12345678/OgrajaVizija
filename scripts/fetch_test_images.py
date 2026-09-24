#!/usr/bin/env python3
"""Prenese prosto dostopne testne slike (balkon + ograja) za realni test pipeline-a.

Viri: Wikimedia Commons (proste licence). Če kateri URL odpove, skripta to prijavi
in ne ustvari lažnega uspeha.
"""
import os, sys, urllib.request, json

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "test")
os.makedirs(OUT, exist_ok=True)

# (ime_datoteke, Wikimedia Special:FilePath naslov, pričakovana licenca)
SOURCES = [
    ("scene_balcony_01.jpg", "Balcony_with_railing_in_Ljubljana.jpg"),
    ("product_railing_01.jpg", "Wrought_iron_railing_detail.jpg"),
    ("scene_balcony_02.jpg", "Balconies_in_Paris.jpg"),
    ("product_railing_02.jpg", "Metal_fence_panel.jpg"),
    ("scene_balcony_03.jpg", "Ornate_balcony_railing.jpg"),
    ("product_railing_03.jpg", "Decorative_iron_railing.jpg"),
    ("scene_balcony_04.jpg", "Modern_balcony_black_railing.jpg"),
    ("product_railing_04.jpg", "Black_aluminium_railing.jpg"),
    ("scene_balcony_05.jpg", "Balcony_vertical_slats.jpg"),
    ("product_railing_05.jpg", "Vertical_batten_fence.jpg"),
]

def fetch(name, wm_file):
    url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + wm_file + "?width=1600"
    dst = os.path.join(OUT, name)
    if os.path.exists(dst) and os.path.getsize(dst) > 20000:
        return "cached"
    req = urllib.request.Request(url, headers={"User-Agent": "OgrajaVizija-TestAssets/0.1 (research)"})
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            data = r.read()
        if len(data) < 5000:
            return "TOO_SMALL(%d)" % len(data)
        open(dst, "wb").write(data)
        return "ok %d KB" % (len(data) // 1024)
    except Exception as e:
        return "FAIL %s: %s" % (type(e).__name__, str(e)[:70])

def main():
    results = {}
    for name, wm in SOURCES:
        results[name] = fetch(name, wm)
        print(f"{name:28s} {results[name]}")
    ok = sum(1 for v in results.values() if v.startswith(("ok", "cached")))
    print(f"\n{ok}/{len(SOURCES)} prenesenih.")
    json.dump(results, open(os.path.join(OUT, "_fetch_report.json"), "w"), indent=1)
    return 0 if ok == len(SOURCES) else 1

if __name__ == "__main__":
    sys.exit(main())
