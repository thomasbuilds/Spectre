"""Regenerates the vendor tables shipped in app/src/main/assets.

oui.txt maps MAC prefixes to organizations, from IEEE's MA-L, MA-M, MA-S and IAB registries.
cid.txt maps Bluetooth company identifiers to companies, from the Bluetooth SIG's assigned numbers.
The Vendor lists workflow runs this monthly and opens a pull request with the result.
"""

import csv
import html
import io
import json
import re
import shutil
import subprocess
import sys
import unicodedata
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "app/src/main/assets"
IEEE_REGISTRIES = {  # prefix length in hex digits
  "https://standards-oui.ieee.org/oui/oui.csv": 6,
  "https://standards-oui.ieee.org/oui28/mam.csv": 7,
  "https://standards-oui.ieee.org/oui36/oui36.csv": 9,
  "https://standards-oui.ieee.org/iab/iab.csv": 9,
}
SIG_COMPANY_IDS = (
  "https://bitbucket.org/bluetooth-SIG/public/raw/main/"
  "assigned_numbers/company_identifiers/company_identifiers.yaml"
)
# Compared without dots, slashes or enclosing parentheses, so "S.A.", "A/S" and "(Pty)" match.
LEGAL_FORMS = {
  "ab", "ag", "aktiebolag", "aktiengesellschaft", "aps", "as", "asa", "aş", "bhd", "bv", "bvba", "co", "coltd",
  "company", "corp", "corporate", "corporation", "cv", "dd", "doo", "ehf", "eireli", "ek", "ev", "gesellschaft",
  "gmbh", "inc", "incorporated", "jsc", "kabushiki", "kaisha", "kft", "kg", "kk", "kommanditgesellschaft", "lda",
  "liability", "limitada", "limited", "llc", "llp", "lp", "ltd", "ltda", "mbh", "nv", "nyrt", "ohg", "ooo", "oy",
  "oyj", "oü", "pjsc", "plc", "private", "proprietary", "pte", "pty", "pvt", "sa", "sapi", "sarl", "sas", "sau",
  "sdn", "se", "sia", "sl", "slu", "sociedad", "spa", "sro", "srl", "sti", "şirketi", "şti", "uab", "ug", "zrt",
}
MULTI_WORD_LEGAL_FORM = re.compile(
  r"\s+(?:s\.?\s?a\.?(?:\s?p\.?\s?i\.?)?|s\.?\s?de\s+r\.?\s?l\.?)\s+de\s+c\.?\s?v\.?$"
  r"|\s+sp\.?\s?z\s?o\.?\s?o\.?$|\s+spol\.?\s+s\s?r\.?\s?o\.?$",
  re.IGNORECASE,
)
APPOSITIVE = re.compile(r",\s*an?\s.*$", re.IGNORECASE)  # ", a division of Brunswick Corporation"
LAST_TOKEN = re.compile(r"[\s,]+([^\s,&]+)$")
GLUED_LAST_WORD = re.compile(r"(?<=\w\w)\.(?=([^\W\d_]+)\.?$)")  # "MFG.CO." -> "MFG. CO."
TRAILING_PARENTHETICAL = re.compile(r"\s*\([^()]*\)$")
DANGLING = re.compile(r"(?:[\s,;:&-]|\s(?:and|und)\b)+$", re.IGNORECASE)  # "Ltd and Co. KG" leaves "Ltd and"
SMALL_WORDS = {
  "and", "da", "das", "de", "del", "der", "des", "di", "do", "dos", "du", "e", "em", "en", "et", "for", "la",
  "le", "of", "the", "und", "van", "ve", "von", "y",
}
WORD = re.compile(r"(?<![\w.])[^\W\d_]+(?![\w])")  # letters only, so "3COM" and "3bumen.com" stay intact
VOWEL = re.compile("[aeiouy]", re.IGNORECASE)  # a long word without one is an acronym, like "CJSC"
MC_NAME = re.compile(r"^Mc([a-z])(?=[a-z]{3})")  # "Mcdonnell" -> "McDonnell"
PUNCTUATION = " ,;:&-"


def fetch(url):
  # curl, not urllib: urllib waits out every unreachable IPv6 address before trying IPv4.
  result = subprocess.run(["curl", "-fsSL", "--retry", "3", "--max-time", "120", url], capture_output=True)
  if result.returncode:
    sys.exit(f"error: cannot download {url}: {result.stderr.decode().strip()}")
  return result.stdout.decode("utf-8-sig")


def ieee_names():
  names = {}
  for url, digits in IEEE_REGISTRIES.items():
    rows = list(csv.reader(io.StringIO(fetch(url))))
    if len(rows) < 1000 or rows[0][:3] != ["Registry", "Assignment", "Organization Name"]:
      sys.exit(f"error: unexpected content from {url}")
    for row in rows[1:]:
      if len(row) < 3 or not re.fullmatch(rf"[0-9A-F]{{{digits}}}", row[1]) or not row[2].strip():
        sys.exit(f"error: unexpected row {row} in {url}")
      _, assignment, organization, *_ = row
      if organization != "IEEE Registration Authority":
        names.setdefault(assignment, organization)
  return names


def sig_names():
  text = fetch(SIG_COMPANY_IDS)
  entries = re.findall(r"^\s*- value: 0x([0-9A-F]{4})\n\s*name: (.+?)\s*$", text, re.MULTILINE)
  names = {value: unquote(name) for value, name in entries}
  if len(names) != text.count("value:") or len(names) < 1000 or not all(name.strip() for name in names.values()):
    sys.exit(f"error: unexpected content from {SIG_COMPANY_IDS}")  # never drop or blank entries silently
  return names


def unquote(scalar):
  if len(scalar) > 1 and scalar[0] == scalar[-1] == "'":
    return scalar[1:-1].replace("''", "'")
  if len(scalar) > 1 and scalar[0] == scalar[-1] == '"':
    return json.loads(scalar)
  sys.exit(f"error: unexpected name {scalar} in {SIG_COMPANY_IDS}")


def is_legal_form(token):
  if token.startswith("(") and token.endswith(")"):
    token = token[1:-1]
  return "(" not in token and ")" not in token and re.sub(r"[./]", "", token).lower() in LEGAL_FORMS


def ends_with_legal_form(name):
  last = LAST_TOKEN.search(name)
  return last is not None and is_legal_form(last[1])


def strip_legal_forms(name):
  name = unicodedata.normalize("NFKC", html.unescape(name))
  name = re.sub(r"\s+(?=[.,])", "", name)  # "CO ., LTD"
  name = re.sub(r"\)(?=\w)", ") ", re.sub(r"(?<=\w)\(", " (", name))  # "Sales(Shandong)Co."
  name = re.sub(r"\s+", " ", name).strip(PUNCTUATION)
  while True:
    name = GLUED_LAST_WORD.sub(lambda dot: ". " if dot[1].lower() in LEGAL_FORMS else ".", name)
    if appositive := APPOSITIVE.search(name):
      end = appositive.start()
    elif multi_word := MULTI_WORD_LEGAL_FORM.search(name):
      end = multi_word.start()
    elif ends_with_legal_form(name):
      end = LAST_TOKEN.search(name).start()
    elif (tail := TRAILING_PARENTHETICAL.search(name)) and ends_with_legal_form(name[: tail.start()]):
      end = tail.start()
    else:
      return re.sub(r"(?<=[^\W\d_]{6})\.$", "", name)  # "TECHNOLOGY.,LTD" leaves a stray dot
    name = DANGLING.sub("", name[:end])


def recase(name, raw):
  """Title-cases names registered in capitals, keeping short words that read as acronyms."""
  if re.fullmatch(r"[a-z]{2,3}", name) and raw == raw.lower():
    return name.upper()  # "zte corporation"
  if name != name.upper() or raw != raw.upper():
    return name  # "NOOLIX Ltd." styles its brand, "NOOLIX LTD." just shouts

  def fix(match):
    word, abbreviated = match[0], name[match.end() : match.end() + 1] == "."
    if word.lower() in SMALL_WORDS and not abbreviated:
      word = word.lower() if match.start() else word.capitalize()
    elif abbreviated or (len(word) > 3 and VOWEL.search(unicodedata.normalize("NFD", word))):
      word = MC_NAME.sub(lambda mc: "Mc" + mc[1].upper(), word.capitalize())
    return word.replace("i\u0307", "i")  # lower() turns the Turkish "İ" into "i" and a combining dot

  return WORD.sub(fix, name)


def write(file, table):
  if not all(table.values()):
    sys.exit(f"error: an entry in {file} has no name left after cleaning")
  path = ASSETS / file
  old = dict(line.split("\t", 1) for line in path.read_text("utf-8").splitlines()) if path.exists() else {}
  with path.open("w", encoding="utf-8", newline="\n") as out:
    out.writelines(f"{key}\t{table[key]}\n" for key in sorted(table))
  renamed = sum(key in old and old[key] != name for key, name in table.items())
  print(f"{file}: {len(table)} entries, {len(table.keys() - old.keys())} added, "
        f"{len(old.keys() - table.keys())} removed, {renamed} renamed")


def main():
  if not shutil.which("curl"):
    sys.exit("error: curl not found")
  tables = {"oui.txt": ieee_names(), "cid.txt": sig_names()}
  for file, names in tables.items():
    write(file, {key: recase(strip_legal_forms(raw), raw) for key, raw in names.items()})


if __name__ == "__main__":
  main()
