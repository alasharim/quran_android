#!/usr/bin/env python3
"""Builds the bundled hadith database that links hadiths to the ayat they explain.

Hadiths come from the tafsir books of Sahih al-Bukhari (book 65) and Sahih Muslim
(book 56 on sunnah.com). In those books every chapter (bab) heading quotes the ayah
the chapter is about, and the Shamela edition of the text cites each quote as
[surah name: ayah number]. Those citations are the links. A link is only kept at
the ayah level when the quoted words are actually found in that ayah; anything
less certain becomes a link to the whole surah instead.

Sources (pinned):
  - Hadith text: fawazahmed0/hadith-api (Unlicense, public domain).
  - Chapter headings and their citations: OpenITI 0275AH corpus, Shamela editions.
  - Quran text used to check the quotes: fawazahmed0/quran-api (Unlicense).

Usage:
  python3 tools/hadith/build_hadith_db.py

Writes feature/hadith/src/main/assets/hadith_v1.db and tools/hadith/hadith_links.csv, the
latter listing every link with the evidence behind it so it can be reviewed.
"""

import csv
import json
import os
import re
import sqlite3
import sys
import urllib.request
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CACHE_DIR = os.path.join(ROOT, "tools", "hadith", ".cache")
DB_PATH = os.path.join(ROOT, "feature", "hadith", "src", "main", "assets", "hadith_v1.db")
CSV_PATH = os.path.join(ROOT, "tools", "hadith", "hadith_links.csv")

HADITH_API = "https://raw.githubusercontent.com/fawazahmed0/hadith-api/df57907be35291c91ad6a6691180e22ca9920784"
QURAN_API = "https://raw.githubusercontent.com/fawazahmed0/quran-api/47ca096b0976443ba2eab2e45cdf0fb4096a2610"
OPENITI = "https://raw.githubusercontent.com/OpenITI/0275AH/44e1c36738a2bf5c14dafa232a6ae1891e6171cd/data"

SOURCES = {
  "ara-bukhari.json": HADITH_API + "/editions/ara-bukhari.json",
  "ara-muslim.json": HADITH_API + "/editions/ara-muslim.json",
  "ara-quransimple.json": QURAN_API + "/editions/ara-quransimple.json",
  "bukhari-shamela.txt":
    OPENITI + "/0256Bukhari/0256Bukhari.Sahih/0256Bukhari.Sahih.Shamela0001681-ara1",
  "muslim-shamela.txt":
    OPENITI + "/0261Muslim/0261Muslim.Sahih/0261Muslim.Sahih.Shamela0001727-ara1.mARkdown",
}

SURA_NAMES = [
  "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
  "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء",
  "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
  "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر",
  "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان",
  "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم",
  "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف",
  "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة",
  "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات",
  "النبأ", "النازعات", "عبس", "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج",
  "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح",
  "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر",
  "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر", "المسد",
  "الإخلاص", "الفلق", "الناس",
]

# Other names the editions use for some surahs.
SURA_ALIASES = {
  "براءة": 9, "بني إسرائيل": 17, "المؤمن": 40, "حم السجدة": 41, "حم عسق": 42,
  "الدهر": 76, "هل أتى": 76, "عم": 78, "عم يتساءلون": 78, "الانشراح": 94, "ألم نشرح": 94,
  "اقرأ": 96, "اقرأ باسم ربك": 96, "لم يكن": 98, "تبت": 111, "تبت يدا أبي لهب": 111,
  "اللهب": 111, "قل هو الله أحد": 112, "الصمد": 112, "قل أعوذ برب الفلق": 113, "قل أعوذ برب الناس": 114, "الذين كفروا": 47,
  "ن": 68, "ن والقلم": 68, "سأل سائل": 70,
  "إذا الشمس كورت": 81, "إذا السماء انفطرت": 82, "ويل للمطففين": 83,
  "إذا السماء انشقت": 84, "سبح اسم ربك الأعلى": 87, "هل أتاك حديث الغاشية": 88,
  "والفجر": 89, "لا أقسم": 90, "والشمس وضحاها": 91, "والليل إذا يغشى": 92,
  "والضحى": 93, "والتين": 95, "إنا أنزلناه": 97, "إذا زلزلت": 99, "والعاديات": 100,
  "القارعة": 101, "ألهاكم": 102, "والعصر": 103, "ويل لكل همزة": 104,
  "ألم تر كيف فعل ربك": 105, "لإيلاف قريش": 106, "أرأيت": 107, "إنا أعطيناك": 108,
  "قل يا أيها الكافرون": 109, "إذا جاء نصر الله": 110, "كهيعص": 19, "الملائكة": 35,
  "حم الزخرف": 43, "حم الدخان": 44, "حم الجاثية": 45, "حم الأحقاف": 46,
  "اقتربت الساعة": 54, "المنافقين": 63, "قل أوحي إلي": 72, "هل أتى على الإنسان": 76,
  "إذا السماء": 82, "ألم نشرح لك": 94, "اقرأ باسم ربك الذي خلق": 96,
  "إذا زلزلت الأرض زلزالها": 99, "ألم تر": 105, "إنا أعطيناك الكوثر": 108,
}

# Muslim's chapter on the surahs Bara'a, al-Anfal and al-Hashr names no ayah.
MUSLIM_SURA_CHAPTERS = {"باب في سورة براءة والأنفال والحشر": [9, 8, 59]}

TASHKEEL = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭـ‌-‏]")
NOISE = re.compile(r"ms\d+|PageV\d+P\d+|</?span>|\[ص: \d+\]")


def fetch(name):
  path = os.path.join(CACHE_DIR, name)
  if not os.path.exists(path):
    os.makedirs(CACHE_DIR, exist_ok=True)
    print("downloading", SOURCES[name])
    with urllib.request.urlopen(SOURCES[name]) as response, open(path + ".tmp", "wb") as out:
      out.write(response.read())
    os.rename(path + ".tmp", path)
  return path


def normalize(text):
  text = TASHKEEL.sub("", text)
  text = re.sub("[أإآٱ]", "ا", text)
  text = text.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "و").replace("ئ", "ي")
  text = text.replace("ء", "")
  text = re.sub(r"[^ء-ي ]", " ", text)
  text = re.sub("وو", "و", text)
  text = re.sub(r"\s+", " ", text).strip()
  # The editions spell أن لا where the mushaf writes ألا.
  return re.sub(r"(^| )ان لا( |$)", r"\1الا\2", text)


SURA_BY_NAME = {normalize(name): index + 1 for index, name in enumerate(SURA_NAMES)}
SURA_BY_NAME.update({normalize(name): sura for name, sura in SURA_ALIASES.items()})


def sura_number(name):
  name = normalize(name)
  if name in SURA_BY_NAME:
    return SURA_BY_NAME[name]
  if name.startswith("ال") and name[2:] in SURA_BY_NAME:
    return SURA_BY_NAME[name[2:]]
  if "ال" + name in SURA_BY_NAME:
    return SURA_BY_NAME["ال" + name]
  if name.startswith("و") and name[1:] in SURA_BY_NAME:
    return SURA_BY_NAME[name[1:]]
  return None


def sura_marker_name(text):
  """'سورة نوح: {إنا أرسلنا} [هود: 70]' names نوح; 'سورة {سأل سائل} [...]' names سأل سائل."""
  text = text.replace("صلى الله عليه وسلم", "")
  before_quote = text.split("{")[0]
  if before_quote.strip():
    text = before_quote
  text = re.split(r"[\[:«]", text)[0]
  return re.sub(r"[\"{}]", " ", text).strip()


def paragraphs(path):
  """Joins the corpus's wrapped lines (prefixed with ~~) into whole paragraphs."""
  result = []
  for line in open(path, encoding="utf-8").read().split("\n"):
    if line.startswith("~~") and result:
      result[-1] += " " + line[2:]
    else:
      result.append(line)
  return result


CITATION = re.compile(r"\{([^{}]*)\}\s*\[([^\[\]:]+):\s*(\d+)\s*\]")


class Quran:
  def __init__(self, path):
    verses = json.load(open(path, encoding="utf-8"))["quran"]
    self.text = {(v["chapter"], v["verse"]): normalize(v["text"]) for v in verses}
    self.verse_counts = {}
    for sura, ayah in self.text:
      self.verse_counts[sura] = max(self.verse_counts.get(sura, 0), ayah)

  def contains(self, sura, ayah, fragment):
    """True when the quoted fragment starts in this ayah (it may run into the next one)."""
    fragment = normalize(fragment)
    if (sura, ayah) not in self.text or len(fragment) < 2:
      return False
    here = self.text[(sura, ayah)]
    following = here + " " + self.text.get((sura, ayah + 1), "")
    if fragment in here:
      return True
    index = following.find(fragment)
    return 0 <= index < len(here)

  def find(self, sura, fragment):
    """The one ayah of the surah the fragment starts in, if there is exactly one."""
    if len(normalize(fragment).split()) < 3:
      return None
    matches = [ayah for ayah in range(1, self.verse_counts.get(sura, 0) + 1)
               if self.contains(sura, ayah, fragment)]
    return matches[0] if len(matches) == 1 else None


def citations(text, quran):
  """Returns [(sura, ayah, fragment, verified)] for each {quote} [surah: ayah] in text."""
  found = []
  for fragment, name, ayah in CITATION.findall(NOISE.sub(" ", text)):
    sura = sura_number(name.replace("~~", " "))
    if sura is None:
      continue
    ayah = int(ayah)
    found.append((sura, ayah, fragment.strip(), quran.contains(sura, ayah, fragment)))
  return found


def clean_heading(text):
  text = NOISE.sub(" ", text).replace("###", "").replace("|", "")
  text = re.sub(r"(^|\s)#\s", " ", text)
  text = re.sub(r'\s*"\s*$', "", text)
  return re.sub(r"\s+", " ", text).strip()


def similarity(a, b):
  def grams(text):
    text = normalize(text).replace(" ", "")
    return {text[i:i + 4] for i in range(len(text) - 3)}
  ga, gb = grams(a), grams(b)
  return len(ga & gb) / max(1, min(len(ga), len(gb)))


class Hadith:
  def __init__(self, marker):
    self.marker = marker
    self.chapter = None
    self.chapter_citations = []
    self.sura = None
    self.text = []


def parse_bukhari(path, quran):
  lines = paragraphs(path)
  start = next(i for i, l in enumerate(lines) if l.startswith("### $ 65 -"))
  end = next(i for i, l in enumerate(lines) if l.startswith("### $ 66 -"))
  hadiths = []
  chapter, chapter_citations, sura = None, [], 1
  current = None
  i = start + 1
  while i < end:
    line = lines[i]
    plain = NOISE.sub(" ", line).strip()
    sura_marker = re.match(r"^#\s*سورة\s+(.+)$", plain)
    hadith_marker = re.match(r"^#{1,3}\s*\|?\s*(\d+)\s*-\s*$", line.strip())
    if line.startswith("### | باب"):
      title = line
      if i + 1 < end and "</span>" in lines[i + 1]:
        title += " " + lines[i + 1]
        i += 1
      chapter = clean_heading(title)
      chapter_citations = citations(title, quran)
      current = None
    elif sura_marker or re.match(r"^#\s*سورة$", plain):
      # The edition leaves one marker (for 'Abasa) without a name.
      name = sura_marker_name(sura_marker.group(1)) if sura_marker else ""
      number = sura_number(name) if name else sura + 1
      if number is None or number <= sura:
        sys.exit("unknown or out of order surah marker: " + line)
      sura, chapter, chapter_citations, current = number, None, [], None
    elif hadith_marker:
      current = Hadith(int(hadith_marker.group(1)))
      current.chapter, current.chapter_citations, current.sura = chapter, chapter_citations, sura
      hadiths.append(current)
    elif line.startswith("###") and not re.match(r"^### \|\s*(\[|$)", line):
      current = None
    elif current is not None:
      current.text.append(line)
    i += 1
  return hadiths


def parse_muslim(path, quran):
  lines = paragraphs(path)
  start = next(i for i, l in enumerate(lines) if re.match(r"^### \| \d+ - كتاب التفسير", l))
  # The tafsir book is the last one in Muslim.
  end = next((i for i in range(start + 1, len(lines)) if re.match(r"^### \| \d+ - كتاب", lines[i])),
             len(lines))
  hadiths = []
  chapter, chapter_citations = None, []
  current = None
  for line in lines[start + 1:end]:
    hadith_marker = re.match(r"^### \$ (\d+) -", line)
    if line.startswith("### || "):
      chapter = re.sub(r"^\d+ - ", "", clean_heading(line))
      chapter_citations = citations(line, quran)
      current = None
    elif hadith_marker:
      current = Hadith(int(hadith_marker.group(1)))
      current.chapter, current.chapter_citations = chapter, chapter_citations
      current.text.append(line)
      hadiths.append(current)
    elif line.startswith("###") and not re.match(r"^### \|\s*(\[|$)", line):
      current = None
    elif current is not None:
      current.text.append(line)
  return hadiths


def links_for(hadith, quran, chapter_suras=None):
  """Returns [(sura, ayah, source, fragment)]; ayah 0 links the whole surah."""
  verified = [c for c in hadith.chapter_citations
              if c[3] and (hadith.sura is None or c[0] == hadith.sura)]
  if verified:
    return [(sura, ayah, "chapter", fragment) for sura, ayah, fragment, _ in verified]
  if chapter_suras:
    return [(sura, 0, "chapter", "") for sura in chapter_suras]
  # Some headings quote the ayah without citing it; look the quote up in the surah.
  if hadith.chapter and hadith.sura is not None:
    found = OrderedDict()
    for fragment in re.findall(r"[{(]([^{}()]{6,})[})]", hadith.chapter):
      ayah = quran.find(hadith.sura, fragment)
      if ayah:
        found.setdefault(ayah, fragment)
    if found:
      return [(hadith.sura, ayah, "chapter_quote", fragment) for ayah, fragment in found.items()]
  in_text = {}
  for sura, ayah, fragment, ok in citations(" ".join(hadith.text), quran):
    in_text.setdefault((sura, ayah), []).append((fragment, ok))
  if len(in_text) == 1:
    (sura, ayah), quotes = next(iter(in_text.items()))
    if (all(ok for _, ok in quotes) and (hadith.sura is None or sura == hadith.sura)
        and len(normalize(quotes[0][0]).split()) >= 2):
      return [(sura, ayah, "hadith_text", quotes[0][0])]
  if hadith.sura is not None:
    return [(hadith.sura, 0, "surah", "")]
  return []


def sunnah_number(arabic_number):
  """fawazahmed0 writes sub-narrations as 3017.01; sunnah.com writes 3017a."""
  whole, _, sub = str(arabic_number).partition(".")
  sub = int(sub or 0)
  return str(whole) + (chr(ord("a") + sub - 1) if sub else "")


def main():
  quran = Quran(fetch("ara-quransimple.json"))
  bukhari_api = json.load(open(fetch("ara-bukhari.json"), encoding="utf-8"))
  muslim_api = json.load(open(fetch("ara-muslim.json"), encoding="utf-8"))

  rows = []  # (collection, number, chapter, text, links, similarity)

  # Bukhari: the Shamela edition and the API share the standard numbering. A few
  # hadiths lost their number marker in the edition; they get the chapter of the
  # hadiths on either side when both sides agree.
  api = {h["hadithnumber"]: h for h in bukhari_api["hadiths"]}
  details = bukhari_api["metadata"]["section_details"]["65"]
  parsed = {h.marker: h for h in parse_bukhari(fetch("bukhari-shamela.txt"), quran)}
  for number in range(details["hadithnumber_first"], details["hadithnumber_last"] + 1):
    if number not in api:
      continue
    hadith = parsed.get(number)
    score = None
    if hadith is None:
      before = max((n for n in parsed if n < number), default=None)
      after = min((n for n in parsed if n > number), default=None)
      if before is None or after is None or parsed[before].chapter != parsed[after].chapter:
        continue
      hadith = Hadith(number)
      neighbour = parsed[before]
      hadith.chapter, hadith.chapter_citations, hadith.sura = (
        neighbour.chapter, neighbour.chapter_citations, neighbour.sura)
    else:
      score = similarity(" ".join(hadith.text), api[number]["text"])
      if score < 0.5:
        print("bukhari", number, "text differs from the API (%.2f), skipped" % score)
        continue
    links = links_for(hadith, quran)
    if links:
      rows.append(("bukhari", str(number), hadith.chapter, api[number]["text"], links, score))

  # Muslim: the edition numbers hadiths within the book, so each narration is
  # matched to the API by its text instead.
  details = muslim_api["metadata"]["section_details"]["56"]
  candidates = [h for h in muslim_api["hadiths"]
                if details["hadithnumber_first"] <= h["hadithnumber"] <= details["hadithnumber_last"]
                and h.get("arabicnumber") and h["text"]]
  used = set()
  for hadith in parse_muslim(fetch("muslim-shamela.txt"), quran):
    text = " ".join(hadith.text)
    best = max(candidates, key=lambda h: similarity(text, h["text"]))
    score = similarity(text, best["text"])
    if score < 0.5 or best["hadithnumber"] in used:
      print("muslim in-book", hadith.marker, "has no clear match (%.2f), skipped" % score)
      continue
    used.add(best["hadithnumber"])
    links = links_for(hadith, quran, MUSLIM_SURA_CHAPTERS.get(hadith.chapter))
    if links:
      rows.append(("muslim", sunnah_number(best["arabicnumber"]), hadith.chapter,
                   best["text"], links, score))

  write_outputs(rows)


def write_outputs(rows):
  if os.path.exists(DB_PATH):
    os.remove(DB_PATH)
  os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
  db = sqlite3.connect(DB_PATH)
  db.executescript("""
    CREATE TABLE hadith(
      id INTEGER NOT NULL PRIMARY KEY,
      collection TEXT NOT NULL,
      number TEXT NOT NULL,
      chapter TEXT,
      text TEXT NOT NULL,
      grade TEXT NOT NULL
    );
    CREATE TABLE hadith_ayah(
      hadith_id INTEGER NOT NULL,
      sura INTEGER NOT NULL,
      ayah INTEGER NOT NULL,
      PRIMARY KEY(sura, ayah, hadith_id)
    );
  """)
  with open(CSV_PATH, "w", newline="", encoding="utf-8") as out:
    writer = csv.writer(out)
    writer.writerow(["collection", "number", "sura", "ayah", "source", "chapter", "quote", "text_match"])
    for hadith_id, (collection, number, chapter, text, links, score) in enumerate(rows, start=1):
      db.execute("INSERT INTO hadith VALUES (?, ?, ?, ?, ?, ?)",
                 (hadith_id, collection, number, chapter, text, "صحيح"))
      for sura, ayah, source, fragment in OrderedDict(((l[0], l[1]), l) for l in links).values():
        db.execute("INSERT INTO hadith_ayah VALUES (?, ?, ?)", (hadith_id, sura, ayah))
        writer.writerow([collection, number, sura, ayah, source, chapter or "", fragment,
                         "" if score is None else "%.2f" % score])
  # Matches the schema version in Hadith.sq so SqlDelight opens the file as is.
  db.execute("PRAGMA user_version = 1")
  db.commit()
  db.execute("VACUUM")
  stats = db.execute("""
    SELECT h.collection, COUNT(DISTINCT h.id),
           COUNT(DISTINCT CASE WHEN a.ayah > 0 THEN a.sura || ':' || a.ayah END),
           COUNT(DISTINCT CASE WHEN a.ayah > 0 THEN h.id END)
    FROM hadith h JOIN hadith_ayah a ON a.hadith_id = h.id GROUP BY h.collection
  """).fetchall()
  db.close()
  for collection, hadiths, ayat, ayah_linked in stats:
    print("%s: %d hadiths, %d linked to specific ayat (%d distinct ayat), %d to a whole surah only"
          % (collection, hadiths, ayah_linked, ayat, hadiths - ayah_linked))
  print("wrote", os.path.relpath(DB_PATH, ROOT), "(%d KB)" % (os.path.getsize(DB_PATH) // 1024))
  print("wrote", os.path.relpath(CSV_PATH, ROOT))


if __name__ == "__main__":
  main()
