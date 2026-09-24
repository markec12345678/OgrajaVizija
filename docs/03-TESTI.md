# 03 · Testi in rezultati

## A. JVM testi jedra — `tools/jvmtest/run_core_tests.sh`

26 testov, brez Androida in brez Gradla (delujejo tudi v okolju z 1 GB RAM):

| Sklop | Kaj preverja | Rezultat |
|---|---|---|
| A · homografija | 4 vogali → H; napaka preslikave | ✅ 5,7·10⁻¹⁴ px |
| A · inverz | H⁻¹(H(p)) = p | ✅ |
| A · degeneracija | podvojen vogal → izjema | ✅ |
| A2 · barvni prostor | L(črna)=0, L(bela)=100, sRGB↔LAB round-trip | ✅ max napaka 0,00/255 |
| B · zaščita originala | `changedOutsideMask == 0`; piksli izven maske bitno enaki | ✅ 0 razlik |
| C · barvno ujemanje | LAB statistika; strength=0 = brez sprememb | ✅ |
| D · senca stika | senca poveča št. spremenjenih pikslov in potemni pod izdelkom | ✅ R 180 → 144 |
| E · lokalni izrez | Otsu + komponenta: α sredice 255, α ozadja 0, bbox (40,60,160,140) | ✅ |
| F · lokalno inpaintanje | Δ od originala 157,7 → 6,2; izven maske nedotaknjeno | ✅ |
| G · realna slika | balkon + črna alu ograja: izrez 926×406, leakage 0.0 | ✅ |

Zadnji zagon: **26 passed, 0 failed**.

Napake, ki so jih testi dejansko ujeli in so bile popravljene:
1. napačna predznaka kofaktorjev v inverzni matriki homografije,
2. Otsu prag leži *v* temnem razredu → primerjava mora biti `g <= t`,
3. polariteta ozadja (rob slike = ozadje) je bila obrnjena,
4. `0xFF shl 24` v Kotlinu vrne negativen Int → alfa kanal je bil pokvarjen,
5. LAB funkcija je namesto kubičnega korena uporabljala napačno vejo za majhne vrednosti.

## B. Realni vizualni test (geometrijska pot, 🟢)

Vhod: fotografija fasade z balkoni + produktna fotografija črne aluminijaste ograje.

Slike se generirajo lokalno v `tools/jvmtest/out/` (v repoju jih ni):
| Datoteka | Pomen |
|---|---|
| `out/real_clean_plate.png` | clean plate po lokalnem inpaintanju maske |
| `out/real_local_pipeline.png` | clean plate + končni lokalni rezultat (perspektiva + senca) |
| `out/real_before_after.png` | montaža PREJ | POTEM |

Metrika: `leakageRatio = 0.0` (nič sprememb izven maske).

## C. Testi 1–5 iz specifikacije (AI finalizacija) — ⚠️ NEIZVEDENO TU

Za izvedbo potrebuje GPU (~8–13 GB VRAM). Priložen skript:

```bash
cd backend && pip install -r requirements.txt torch diffusers transformers
python3 scripts/test_pipeline.py --scene TEST1.jpg --product OGRAJA1.jpg \
    --placement placement.json --provider FLUX2_KLEIN_4B --out out/
```

Skript za vsak test zapiše `*_clean.png`, `*_composite.png`, `*_final.png` in
`*_report.json` z `leakage_ratio`. **Kriterij sprejema:** `leakage_ratio == 0` in
vizualna kontrola, da vzorec ograje ni spremenjen (zahteva 16).

| Test | Scenarij | Status |
|---|---|---|
| 1 | realen balkon + kovinska ograja iz skladišča | ️ čaka GPU |
| 2 | ograja posneta pod drugim kotom | ⚠️ čaka GPU |
| 3 | dekorativni vzorec | ⚠️ čaka GPU |
| 4 | črna aluminijasta ograja | ✅ geometrijska pot preverjena (glej B) |
| 5 | navpične letvice | ⚠️ čaka GPU |

## D. CI

`.github/workflows/android.yml`: na `ubuntu-latest` zgradi **debug in release APK**,
naloži jih kot artifact in zažene JVM teste jedra (padec, če `leakage != 0`).
