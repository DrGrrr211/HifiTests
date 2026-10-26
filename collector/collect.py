#!/usr/bin/env python3
"""Sammelt HiFi-Testberichte aus frei verfügbaren RSS-Feeds.

Ergebnis: data/reviews.json (alle Tests) und data/sources_status.json (welche Quelle lief).
Gespeichert werden nur Titel, Link, Quelle, Datum, ein kurzer Anreißer und
herausgelesene Eckdaten (Kategorie, Marke, Preis, Watt, Technologie, Wertung).
Nur Standardbibliothek, damit der GitHub-Lauf nichts installieren muss.
"""
import hashlib
import html
import json
import re
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
SOURCES = Path(__file__).resolve().parent / "sources.json"
UA = "Mozilla/5.0 (Linux; Android 14) HifiTestsCollector/1.0 (+github.com/DrGrrr211/HifiTests)"
MAX_ITEMS = 6000
MAX_PAGE_FETCH_PER_SOURCE = 40
TEASER_LEN = 280

# ---------------------------------------------------------------- Erkennung

REVIEW_PATTERNS = [
    r"\bim test\b", r"^test\b", r"\btest:", r"\btestbericht", r"\bim vergleich", r"\bvergleichstest",
    r"\bim hörtest", r"\bim praxistest", r"\breview\b", r"\breviewed\b", r"\btested\b",
    r"\bhands-on\b", r"\bvs\.?\b", r"\bversus\b", r"\bmeasurements?\b", r"\bkurztest",
]
NOT_REVIEW = [r"\bdeal\b", r"\bangebot", r"\bblack friday", r"\bschnäppchen", r"\bsale\b", r"\bprime day"]

# Reihenfolge zählt: spezifische Kategorien zuerst.
CATEGORIES = [
    ("kopfhoererverstaerker", [r"kopfhörerverstärker", r"kopfhörer-verstärker", r"headphone amp", r"headphone amplifier", r"\bkhv\b", r"dac/amp", r"dac and headphone amp"]),
    ("streaming_verstaerker", [r"streaming-verstärker", r"streamingverstärker", r"streaming amp", r"streaming amplifier", r"netzwerk-verstärker", r"all-in-one streamer", r"streaming-vollverstärker"]),
    ("inear", [r"in-ear", r"\biem\b", r"earbuds?", r"true wireless", r"\btws\b", r"ohrhörer"]),
    ("kopfhoerer_overear", [r"over-ear", r"kopfhörer", r"headphones?", r"over ear", r"planar headphone", r"electrostatic headphone"]),
    ("subwoofer", [r"subwoofer", r"\bsub\b"]),
    ("standlautsprecher", [r"standlautsprecher", r"standbox", r"floorstand", r"floor-stand", r"tower speaker", r"säulenlautsprecher"]),
    ("regallautsprecher", [r"regallautsprecher", r"kompaktlautsprecher", r"kompaktbox", r"bookshelf", r"standmount", r"stand-mount", r"monitorlautsprecher"]),
    ("aktivlautsprecher", [r"aktivlautsprecher", r"aktivbox", r"active speaker", r"powered speaker", r"wireless speaker", r"streaming-lautsprecher"]),
    ("vor_endstufe", [r"vorverstärker", r"endstufe", r"endverstärker", r"preamp", r"power amp", r"monoblock", r"pre-amplifier", r"power amplifier"]),
    ("vollverstaerker", [r"vollverstärker", r"integrated amp", r"integrated amplifier", r"verstärker", r"amplifier", r"\bamp\b"]),
    ("dac", [r"d/a-wandler", r"\bdac\b", r"wandler"]),
    ("streamer", [r"streamer", r"netzwerkplayer", r"netzwerk-player", r"network player", r"music server"]),
    ("plattenspieler", [r"plattenspieler", r"turntable", r"laufwerk"]),
    ("tonabnehmer", [r"tonabnehmer", r"cartridge", r"\bmc-system", r"\bmm-system", r"phono-vorverstärker", r"phonovorstufe", r"phono stage", r"phono preamp"]),
    ("cd_player", [r"cd-player", r"cd player", r"sacd", r"cd-spieler"]),
    ("lautsprecher", [r"lautsprecher", r"speakers?", r"loudspeakers?", r"boxen"]),
]

TECHNOLOGIES = [
    ("Bändchen", [r"bändchen", r"\bribbon\b", r"\bamt\b", r"air motion", r"heil-"]),
    ("Planarmagnetisch", [r"planar", r"magnetostat", r"orthodynam"]),
    ("Elektrostatisch", [r"elektrostat", r"electrostat", r"\bestat\b"]),
    ("Röhre", [r"röhre", r"\btube\b", r"valve amp"]),
    ("Class D", [r"class[- ]d\b", r"klasse[- ]d\b", r"gan-"]),
    ("Class A", [r"class[- ]a\b", r"klasse[- ]a\b"]),
    ("Koaxial", [r"koaxial", r"coaxial", r"uni-q", r"tannoy dual"]),
    ("Breitbänder", [r"breitbänder", r"full-range driver", r"fullrange"]),
    ("Horn", [r"\bhorn\b", r"hornlautsprecher"]),
    ("Dipol", [r"\bdipol", r"\bdipole"]),
    ("Kabellos", [r"bluetooth", r"wireless", r"kabellos", r"\banc\b", r"noise cancelling"]),
    ("Offen", [r"open-back", r"\bopen back", r"\boffene[rns]?\b", r"\boffen\b"]),
    ("Geschlossen", [r"closed-back", r"\bclosed back", r"\bgeschlossene[rns]?\b", r"\bgeschlossen\b"]),
]

BRANDS = """
Accuphase; Acoustic Energy; Acoustic Solid; Acoustic Signature; Advance Paris; AKG; Arcam; Arendal; Ascendo; Astell&Kern;
ATC; Audeze; Audio Physic; Audio-Technica; Audiolab; AudioQuest; Audionet; Audiovector; Audioengine; Auralic; Aurender;
Austrian Audio; Avantgarde; Avid; Ayre; B&O; Bang & Olufsen; B&W; Bowers & Wilkins; Benz Micro; Beyerdynamic; Bluesound;
Børresen; Bose; Boulder; Brinkmann; Bryston; Buchardt; Burmester; Cabasse; Cambridge Audio; Canton; Cayin; CH Precision;
Chord; Clearaudio; Constellation; Cyrus; Dali; DALI; Dan Clark Audio; dCS; Denafrips; Denon; Devialet; Dutch & Dutch;
Dynaudio; Dynavector; Edifier; Elac; ELAC; Electrocompaniet; Elipson; EMT; Epos; Esoteric; Estelon; Eversolo; Exposure;
Falcon Acoustics; Feliks Audio; Ferrum; Fezz; FiiO; Fiio; Final Audio; Focal; Fyne Audio; Gauder; Genelec; Gold Note;
Goldring; Grado; Graham Audio; Gustard; Hana; Harbeth; Heco; Hegel; HiFiMan; HIFIMAN; Hifiman; Holo Audio; iFi;
Innuos; Inklang; Jamo; JBL; JDS Labs; Kanto; KEF; Kii Audio; Klipsch; KLH; Koss; Lindemann; Line Magnetic; Linn; Luxman;
Lyngdorf; Lyra; Magico; Magnat; Magnepan; Marantz; Mark Levinson; MartinLogan; Martin Logan; Matrix Audio; McIntosh;
Meze; Michell; Micromega; Mission; MoFi; Monitor Audio; Moon by Simaudio; Moondrop; Musical Fidelity; Mytek; NAD; Nagra;
Naim; Neat Acoustics; Neumann; Nubert; Octave; Onkyo; Ortofon; Parasound; Pass Labs; Perlisten; Philips; Piega; PMC;
Polk; Primare; ProAc; Pro-Ject; Q Acoustics; Quad; Quadral; Questyle; Raidho; Rega; Revel; RME; Rogers; Rosso Fiorentino;
Rotel; Ruark; Schiit; Sendy Audio; Sennheiser; Shure; Silent Angel; Simaudio; SMSL; Sonoro; Sonos; Sonus Faber;
Sonus faber; Sony; Soulution; Spendor; SPL; Stax; STAX; Stenheim; T+A; Tannoy; Teac; TEAC; Technics; Teufel; Thorens;
Topping; Totem; Transrotor; Triangle; Ultrasone; Unison Research; Vincent; Vitus; Vivid Audio; VPI; Warwick; Wavemaster;
Weiss; Wharfedale; WiiM; Wilson Audio; Woo Audio; Yamaha; YG Acoustics; Zu Audio; Apple; Leben
"""
BRAND_LIST = sorted({b.strip() for b in BRANDS.replace("\n", " ").split(";") if b.strip()}, key=len, reverse=True)
BRAND_CANON = {"B&W": "Bowers & Wilkins", "B&O": "Bang & Olufsen", "DALI": "Dali", "ELAC": "Elac", "HIFIMAN": "HiFiMan",
               "Hifiman": "HiFiMan", "Fiio": "FiiO", "Martin Logan": "MartinLogan", "Sonus faber": "Sonus Faber",
               "STAX": "Stax", "TEAC": "Teac", "Moon by Simaudio": "Simaudio"}

GRADE_WORDS = [
    ("referenz", 98), ("überragend", 97), ("highlight", 95), ("exzellent", 94), ("hervorragend", 93),
    ("sehr gut", 90), ("gut", 78), ("befriedigend", 64), ("ausreichend", 50), ("mangelhaft", 30),
]

def rx_any(patterns, text):
    return any(re.search(p, text) for p in patterns)

def is_review(title, url, cats, forum):
    t = (title + " " + " ".join(cats)).lower()
    if rx_any(NOT_REVIEW, t):
        return False
    if forum:
        return bool(re.search(r"\breview\b|\bmeasurements?\b|\btest\b", title.lower()))
    if re.search(r"/(reviews?|tests?|testberichte?|equipment-?reports?|einzeltest|vergleichstest)/", url.lower()):
        return True
    if any(c.lower() in ("test", "tests", "testberichte", "reviews", "review", "equipment reports", "einzeltest", "vergleichstest") for c in cats):
        return True
    return rx_any(REVIEW_PATTERNS, t)

def detect_category(text):
    t = text.lower()
    for cid, pats in CATEGORIES:
        if rx_any(pats, t):
            return cid
    return "sonstiges"

def detect_tech(text):
    t = text.lower()
    return [name for name, pats in TECHNOLOGIES if rx_any(pats, t)]

def detect_brand(text):
    for b in BRAND_LIST:
        if re.search(r"(?<![\w&])" + re.escape(b) + r"(?![\w&])", text):
            return BRAND_CANON.get(b, b)
    return None

PRICE_RX = [
    (r"(\d{1,3}(?:[.\s]\d{3})+|\d{2,6})(?:,\d{2}|,-)?\s?(?:€|euro\b|eur\b)", "EUR"),
    (r"€\s?(\d{1,3}(?:[.,]\d{3})+|\d{2,6})", "EUR"),
    (r"(?:preis|paarpreis|stückpreis)[^0-9€$£]{0,25}(\d{1,3}(?:[.,]\d{3})+|\d{2,6})", "EUR"),
    (r"£\s?(\d{1,3}(?:,\d{3})+|\d{2,6})", "GBP"),
    (r"\$\s?(\d{1,3}(?:,\d{3})+|\d{2,6})", "USD"),
]
# grobe Umrechnung, damit Preisfilter quellenübergreifend funktionieren
TO_EUR = {"EUR": 1.0, "GBP": 1.17, "USD": 0.93}

def detect_price(text):
    """Liefert (Preis in Euro gerundet, Originalwährung) oder (None, None)."""
    t = text.lower()
    for rx, cur in PRICE_RX:
        m = re.search(rx, t)
        if m:
            raw = re.sub(r"[.,\s]", "", m.group(1))
            try:
                v = int(raw)
            except ValueError:
                continue
            if 15 <= v <= 500000:
                return int(round(v * TO_EUR[cur])), cur
    return None, None

def detect_watt(text):
    m = re.search(r"(\d{1,4})\s?(?:x\s?\d{1,4}\s?)?(?:watt|w)\b(?!\w)", text.lower())
    if m:
        v = int(m.group(1))
        if 1 <= v <= 5000:
            return v
    m = re.search(r"\d\s?x\s?(\d{1,4})\s?(?:watt|w)\b", text.lower())
    return int(m.group(1)) if m else None

def detect_rating_text(text):
    """Wertung aus Fließtext. Ergebnis 0–100 plus Original."""
    t = text.lower()
    m = re.search(r"(\d(?:[.,]\d)?)\s?(?:von|/|out of)\s?5\s?(?:sternen|sterne|stars)", t)
    if m:
        v = float(m.group(1).replace(",", "."))
        if 0 < v <= 5:
            return round(v * 20), m.group(0)
    m = re.search(r"(?:rating|wertung|score|gesamtwertung)[^0-9]{0,15}(\d{1,2}(?:[.,]\d)?)\s?/\s?10\b", t)
    if m:
        v = float(m.group(1).replace(",", "."))
        if 0 < v <= 10:
            return round(v * 10), m.group(0)
    m = re.search(r"(?:gesamtnote|note|testnote)[:\s]+([1-5][.,]\d)\b", t)
    if m:
        v = float(m.group(1).replace(",", "."))
        return round(max(0, min(100, (6 - v) / 5 * 100))), m.group(0)
    m = re.search(r"(?:testurteil|urteil|fazit|bewertung)[:\s]+(referenz|überragend|highlight|exzellent|hervorragend|sehr gut|gut|befriedigend|ausreichend|mangelhaft)", t)
    if m:
        word = m.group(1)
        return dict(GRADE_WORDS)[word], m.group(0)
    m = re.search(r"(\d{1,3})\s?%\s?(?:gesamt|overall|klang|score)", t)
    if m and 0 < int(m.group(1)) <= 100:
        return int(m.group(1)), m.group(0)
    return None, None

def rating_from_jsonld(page):
    for block in re.findall(r'<script[^>]*application/ld\+json[^>]*>(.*?)</script>', page, re.S | re.I):
        try:
            data = json.loads(block.strip())
        except Exception:
            continue
        stack = [data]
        while stack:
            node = stack.pop()
            if isinstance(node, list):
                stack.extend(node)
                continue
            if not isinstance(node, dict):
                continue
            rr = node.get("reviewRating")
            if isinstance(rr, dict) and rr.get("ratingValue") not in (None, ""):
                try:
                    v = float(str(rr["ratingValue"]).replace(",", "."))
                    best = float(str(rr.get("bestRating") or (5 if v <= 5 else 10 if v <= 10 else 100)).replace(",", "."))
                    if best > 0 and 0 < v <= best:
                        return round(v / best * 100), f"{rr['ratingValue']}/{rr.get('bestRating') or int(best)}"
                except Exception:
                    pass
            stack.extend(v for v in node.values() if isinstance(v, (dict, list)))
    return None, None

def price_from_jsonld(page):
    m = re.search(r'"price"\s*:\s*"?(\d{2,6}(?:\.\d{1,2})?)"?', page)
    if m:
        v = int(float(m.group(1)))
        if 15 <= v <= 500000:
            return v
    return None

def product_name(title):
    t = html.unescape(title).strip()
    t = re.sub(r"^(test|review|kurztest|einzeltest|vergleichstest|testbericht)\s*[:\-–|]\s*", "", t, flags=re.I)
    m = re.match(r"(.+?)\s+(?:im (?:test|hörtest|praxistest|vergleich)|review|reviewed|tested|measurements?|messungen)\b", t, re.I)
    if m:
        t = m.group(1)
    t = re.split(r"\s+[–—|]\s+|:\s+|\s+-\s+", t)[0]
    return t.strip(" .,:;\"'“”„")[:90]

def norm_key(brand, name):
    s = (name or "").lower()
    s = re.sub(r"[^a-z0-9äöüß+ ]", " ", s)
    s = re.sub(r"\b(the|der|die|das|und|and|mit|with|new|neu|neue|neuer)\b", " ", s)
    return re.sub(r"\s+", " ", s).strip()

def clean_html(s):
    s = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", s or "", flags=re.S | re.I)
    s = re.sub(r"<[^>]+>", " ", s)
    return re.sub(r"\s+", " ", html.unescape(s)).strip()

def teaser(s):
    s = clean_html(s)
    s = re.sub(r"\s*(The post|Der Beitrag) .* (appeared first on|erschien zuerst auf) .*$", "", s)
    if len(s) <= TEASER_LEN:
        return s
    cut = s[:TEASER_LEN].rsplit(" ", 1)[0]
    return cut + " …"

# ---------------------------------------------------------------- Abruf

def fetch(url, timeout=25):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/rss+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        raw = r.read(4_000_000)
        enc = r.headers.get_content_charset() or "utf-8"
    return raw.decode(enc, errors="replace")

def parse_date(s):
    if not s:
        return None
    s = s.strip()
    try:
        d = parsedate_to_datetime(s)
    except Exception:
        try:
            d = datetime.fromisoformat(s.replace("Z", "+00:00"))
        except Exception:
            return None
    if d.tzinfo is None:
        d = d.replace(tzinfo=timezone.utc)
    return d.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def local(tag):
    return tag.rsplit("}", 1)[-1].lower()

def parse_feed(text):
    """RSS 2.0, RSS 1.0 und Atom. Liefert dicts mit title, url, date, desc, cats."""
    text = re.sub(r"^[^<]+", "", text)
    root = ET.fromstring(text.encode("utf-8"))
    items = []
    for el in root.iter():
        name = local(el.tag)
        if name not in ("item", "entry"):
            continue
        d = {"title": "", "url": "", "date": None, "desc": "", "cats": []}
        for c in el:
            n = local(c.tag)
            if n == "title":
                d["title"] = clean_html(c.text or "")
            elif n == "link":
                href = c.attrib.get("href")
                rel = c.attrib.get("rel", "alternate")
                if href and rel == "alternate":
                    d["url"] = href.strip()
                elif (c.text or "").strip() and not d["url"]:
                    d["url"] = c.text.strip()
            elif n in ("pubdate", "published", "updated", "date") and not d["date"]:
                d["date"] = parse_date(c.text)
            elif n in ("description", "summary") and not d["desc"]:
                d["desc"] = c.text or ""
            elif n == "encoded" and not d["desc"]:
                d["desc"] = c.text or ""
            elif n == "content" and not d["desc"]:
                d["desc"] = c.text or ""
            elif n == "category":
                v = c.attrib.get("term") or c.text or ""
                if v.strip():
                    d["cats"].append(v.strip())
        if d["title"] and d["url"]:
            items.append(d)
    return items

def enrich_from_page(rec):
    try:
        page = fetch(rec["url"], timeout=20)
    except Exception:
        return False
    rating, raw = rating_from_jsonld(page)
    body = clean_html(page)
    if rating is None:
        rating, raw = detect_rating_text(body)
    if rating is not None:
        rec["rating"], rec["ratingRaw"] = rating, raw
    if rec.get("priceEur") is None:
        jp = price_from_jsonld(page)
        if jp:
            rec["priceEur"], rec["priceCurrency"] = jp, "EUR" if rec["lang"] == "de" else None
        else:
            rec["priceEur"], rec["priceCurrency"] = detect_price(body[:20000])
    if rec.get("watt") is None and rec["category"] in ("vollverstaerker", "streaming_verstaerker", "vor_endstufe", "kopfhoererverstaerker", "aktivlautsprecher", "subwoofer"):
        rec["watt"] = detect_watt(body[:30000])
    techs = set(rec.get("technology") or []) | set(detect_tech(body[:30000]))
    rec["technology"] = sorted(techs)
    return True

def build_record(src, it):
    title = it["title"]
    desc_txt = clean_html(it["desc"])
    blob = f"{title} {' '.join(it['cats'])} {desc_txt}"
    name = product_name(title)
    brand = detect_brand(title) or detect_brand(desc_txt[:400])
    cat = detect_category(f"{title} {' '.join(it['cats'])}")
    if cat == "sonstiges":
        cat = detect_category(desc_txt[:600])
    rec = {
        "id": hashlib.sha1(it["url"].encode()).hexdigest()[:16],
        "title": title,
        "product": name,
        "productKey": norm_key(brand, name),
        "url": it["url"],
        "source": src["name"],
        "sourceId": src["id"],
        "lang": src["lang"],
        "date": it["date"] or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "category": cat,
        "brand": brand,
        "priceEur": None,
        "priceCurrency": None,
        "watt": detect_watt(blob) if cat in ("vollverstaerker", "streaming_verstaerker", "vor_endstufe", "kopfhoererverstaerker", "aktivlautsprecher", "subwoofer") else None,
        "technology": detect_tech(blob),
        "rating": None,
        "ratingRaw": None,
        "teaser": teaser(it["desc"]),
    }
    rec["priceEur"], rec["priceCurrency"] = detect_price(blob)
    r, raw = detect_rating_text(blob)
    if r is not None:
        rec["rating"], rec["ratingRaw"] = r, raw
    return rec

def main(argv):
    offline = "--no-pages" in argv
    DATA.mkdir(exist_ok=True)
    out_path = DATA / "reviews.json"
    existing = {}
    if out_path.exists():
        try:
            for r in json.loads(out_path.read_text("utf-8"))["reviews"]:
                existing[r["url"]] = r
        except Exception:
            pass
    sources = json.loads(SOURCES.read_text("utf-8"))
    status = []
    new_total = 0
    for src in sources:
        st = {"id": src["id"], "name": src["name"], "ok": False, "feed": None, "items": 0, "reviews": 0, "new": 0, "error": None}
        items = None
        for feed in src["feeds"]:
            try:
                items = parse_feed(fetch(feed))
                st["ok"], st["feed"] = True, feed
                break
            except Exception as e:
                st["error"] = f"{type(e).__name__}: {e}"[:200]
        if items is None:
            status.append(st)
            print(f"[FEHLER] {src['name']}: {st['error']}")
            continue
        st["error"] = None
        st["items"] = len(items)
        fetched = 0
        for it in items:
            if not is_review(it["title"], it["url"], it["cats"], src.get("forum", False)):
                continue
            st["reviews"] += 1
            if it["url"] in existing:
                continue
            rec = build_record(src, it)
            if not offline and fetched < MAX_PAGE_FETCH_PER_SOURCE:
                fetched += 1
                enrich_from_page(rec)
                time.sleep(1.0)
            existing[it["url"]] = rec
            st["new"] += 1
            new_total += 1
        status.append(st)
        print(f"[OK] {src['name']}: {st['items']} Einträge, {st['reviews']} Tests, {st['new']} neu")
    reviews = sorted(existing.values(), key=lambda r: r["date"], reverse=True)[:MAX_ITEMS]
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    out_path.write_text(json.dumps({"updated": now, "count": len(reviews), "reviews": reviews}, ensure_ascii=False, indent=0), "utf-8")
    (DATA / "sources_status.json").write_text(json.dumps({"updated": now, "sources": status}, ensure_ascii=False, indent=1), "utf-8")
    print(f"Fertig: {len(reviews)} Tests gesamt, {new_total} neu.")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
