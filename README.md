# 🏗 OgrajaVizija

[![android-apk](https://github.com/markec12345678/OgrajaVizija/actions/workflows/android.yml/badge.svg)](https://github.com/markec12345678/OgrajaVizija/actions/workflows/android.yml)

**Roksal WoodCore AI konfigurator za Android.**

OgrajaVizija je aplikacija za pripravo Roksal projektov: od fotografije prostora in
konfiguracije WoodCore profila do vizualizacije, informativnega materialnega izračuna
in strukturiranega povpraševanja.

## Glavni tok

```
1 📷 Prostor
   ↓
2 🧰 Roksal konfiguracija
   ↓
3 ✏️ Označi območje za zamenjavo
   ↓
4 📐 Perspektiva in položaj
   ↓
5 ✨ Vizualizacija
   ↓
6 📩 Roksal povpraševanje
```

## Roksal katalog

Konfigurator trenutno podpira:

- OGRAJA
- PREGRADNA_STENA
- TERASA
- FASADA
- NAPUSC

Pri ograjah so modelirani pokončni in prečni WoodCore sistemi:

- POLNA DESKA 57/32
- POLNA DESKA 100
- POLNA DESKA 128
- ROMB DESKA 67
- DESKA 150

Pri pregradah in fasadah je vključen tudi KUBO 80/42.

Katalog ima datum preverbe **2026-09-24**. Posamezen profil vsebuje tudi povezavo do
svojega uradnega Roksal vira. Ko Roksal spremeni ponudbo ali montažna pravila, je treba
katalog ponovno preveriti.

## Funkcionalnost

Aplikacija zbira:

- fotografijo prostora,
- izbor kategorije, profila, barve, smeri in razmaka,
- način polaganja/površino, kjer je relevantno,
- mere, segmente in status meritve,
- konstrukcijo, vrata in način pritrditve pri ograjah,
- podlago, padec in podkonstrukcijo pri terasah,
- odprtine in KUBO ojačitev pri fasadah,
- dostavo, razrez in montažo,
- podatke fizične osebe ali podjetja,
- računski, projektni in dostavni naslov,
- soglasja in pogoje.

Pri OGRAJI je na voljo avtomatska segmentacija z MediaPipe ter ročni popravek.
Pri drugih kategorijah je izbira območja namenoma ročna.

## Tehnični validator

Konfigurator preverja združljivost profila in smeri, maksimalne razmake,
obvezno aluminijasto jedro pri ROMB/KUBO ter posebne pogoje za fasade,
terase in prečne izvedbe.

Opozorilo ni isto kot potrjena tehnična izvedba. Končno konstrukcijo in ponudbo
potrdi Roksal oziroma monter.

**AI ne sme izmišljati tehničnih pravil ali cen.**

## Materialni izračun

Aplikacija pripravi informativni BOM z deskami, zalogo, nosilci/stebri,
vijaki, ročajem in izbranimi dodatnimi komponentami.

Izračun ni končni Roksal razrez. Končni razrez je odvisen od dejanskih segmentov,
detajlov, vrat, spojev in izvedbe na objektu.

## Vizualizacija

Izdelek se v obstoječe območje vstavi deterministično s perspektivno geometrijo.
AI finalizacija je dodatni strežniški korak.

Osnovna zaščita projekta zahteva, da so spremembe izven maske preverljive;
jedrni testi v projektu preverjajo tudi originalno zaščito.

## Povpraševanje

RoksalQuoteScreen pripravi strukturiran povzetek:

- konfiguracija,
- mere,
- konstrukcija,
- dostava,
- razrez,
- podatki stranke,
- naslovi,
- materialni izračun,
- tehnična opozorila,
- fotografije.

Podpora sta:

1. sistemsko deljenje po e-pošti/drugi aplikaciji, z izbranimi fotografijami,
2. odprtje uradnega Roksal spletnega obrazca.

Poleg sistemskega deljenja in uradnega obrazca je na voljo tudi lastni zaščiten
Roksal inquiry inbox. Ko je na backendu nastavljen `OVIZ_INQUIRY_TOKEN` in je
token v aplikaciji shranjen v Nastavitvah, aplikacija pošlje strukturiran projekt
in do 25 MB izbranih fotografij na `POST /inquiries`. Status je mogoče nato
spreminjati prek lastnega backend API-ja. To ni Roksalov CRM; gre za lastno
strežniško mapo/pipeline brez zunanje integracije.

## Cene

Aplikacija brez potrjenega Roksal cenika **ne prikazuje izmišljene cene**.

Ko je na voljo dejanski cenik, ga je treba dodati kot ločen podatkovni vir,
z datumom veljavnosti in virom.

## Projektni pipeline

```
DRAFT
→ CONFIGURED
→ VISUALIZED
→ QUOTE_REQUESTED
→ ROKSAL_REVIEW
→ SITE_MEASUREMENT
→ OFFER_SENT
→ ACCEPTED
→ INSTALLATION
→ COMPLETED
```

Dodatno je možen CANCELLED.

Interni pregled projektov vsebuje dva vira: lokalne projekte na napravi in lastni
Roksal inbox na backendu. Inbox uporablja bearer token in shranjuje projektne podatke
ter priponke na disku backenda. Za večuporabniško produkcijsko okolje so še vedno
priporočljivi obrat uporabnikov, granularne pravice in namenski podatkovni sloj.

## Android

- Kotlin + Jetpack Compose
- minSdk 26
- targetSdk 35
- MediaPipe Tasks Vision
- ONNX Runtime
- Coil
- kotlinx.serialization
- neobvezen lastni FastAPI backend

GitHub Actions zgradi debug in release APK ter požene JVM jedrne teste.
Android workflow uporablja concurrency in timeout. Ločen backend workflow preveri
Python compile + Roksal inquiry API smoke-test.

## Omejitve

Dokazano je:

- geometrijsko jedro in homografija,
- zaščita originala izven maske,
- lokalni pipeline,
- Android APK build,
- JVM testi,
- avtomatska pomoč pri segmentaciji OGRAJE.

Še vedno je odvisno od zunanje konfiguracije:

- celotna strežniška AI finalizacija zahteva GPU backend,
- končni Roksal cenik ni vključen brez dejanskega cenika,
- neposreden CRM/API ni integriran brez uradne API specifikacije,
- katalog je treba ponovno preveriti ob Roksalovih spremembah.

## Licenca

Projekt in tretje osebe so dokumentirani v LICENSE, NOTICE in docs/01-RAZISKAVA.md.
