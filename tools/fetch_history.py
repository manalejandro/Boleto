#!/usr/bin/env python3
"""
Downloads the full historical draw archive for La Primitiva and EuroMillones
from the official Loterías y Apuestas del Estado (LAE) web service and writes
compact CSV assets used by the Boleto Android app.

Usage:
    python3 tools/fetch_history.py [--out app/src/main/assets]

The LAE "buscadorSorteos" endpoint returns at most 115 draws per request, so
the script walks the calendar in 180-day windows and merges the results.

CSV format
    La Primitiva : date;n1;n2;n3;n4;n5;n6;complementario;reintegro;jackpot
    EuroMillones : date;n1;n2;n3;n4;n5;star1;star2;jackpot
Dates are ISO-8601 (YYYY-MM-DD) and rows are sorted oldest -> newest.
The jackpot is the estimated prize for that draw, in euros.
Empty values are allowed (very old La Primitiva draws had no reintegro).
"""

import argparse
import datetime as dt
import json
import re
import sys
import time
import urllib.request

LAE_URL = (
    "https://www.loteriasyapuestas.es/servicios/buscadorSorteos"
    "?game_id={game}&celebrados=true"
    "&fechaInicioInclusiva={start}&fechaFinInclusiva={end}"
)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "es-ES,es;q=0.9,en;q=0.8",
    "Referer": "https://www.loteriasyapuestas.es/es/resultados",
    "Origin": "https://www.loteriasyapuestas.es",
    "Sec-Fetch-Dest": "empty",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Site": "same-origin",
}

GAMES = {
    # game_id, output file, first date to query, number of extra tokens
    "LAPR": ("history_lapr.csv", "1985-10-01", "complementario+reintegro"),
    "EMIL": ("history_emil.csv", "2004-01-01", "stars"),
}

WINDOW_DAYS = 180
MAX_RESULTS = 115  # hard limit of the LAE endpoint


def fetch(game: str, start: dt.date, end: dt.date) -> list:
    url = LAE_URL.format(
        game=game, start=start.strftime("%Y%m%d"), end=end.strftime("%Y%m%d")
    )
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=30) as resp:
        payload = resp.read().decode("utf-8")
    data = json.loads(payload)
    if not isinstance(data, list):
        raise RuntimeError(f"unexpected payload: {data!r}")
    return data


def parse_combinacion(raw: str):
    """'04 - 07 - 08 - 22 - 40 - 49 C(44) R(2)' -> (6 numbers, comp, reintegro)
    ' 01 - 07 - 10 - 26 - 34 - 02 - 04'          -> (5 numbers, 2 stars)"""
    text = raw.replace("\u00a0", " ").strip()
    comp = re.search(r"C\(\s*(\d*)\s*\)", text)
    re_int = re.search(r"R\(\s*(\d*)\s*\)", text)
    cleaned = re.sub(r"C\([^)]*\)", "", text)
    cleaned = re.sub(r"R\([^)]*\)", "", cleaned)
    numbers = [int(n) for n in re.findall(r"\d+", cleaned)]
    return numbers, (comp.group(1) if comp else None), (re_int.group(1) if re_int else None)


def parse_joker(item: dict) -> str:
    """The winning Joker code of a La Primitiva draw (7 digits, leading zeros
    are significant). The Joker game started in 2012, older draws have none."""
    joker = (item.get("joker") or {}).get("combinacion") or ""
    joker = joker.strip()
    return joker if len(joker) == 7 and joker.isdigit() else ""


def download_game(game: str, first_day: dt.date, today: dt.date) -> dict:
    """Returns {iso_date: [tokens...]} with one entry per draw."""
    draws: dict[str, list[str]] = {}
    start = first_day
    total_requests = 0
    while start <= today:
        end = min(start + dt.timedelta(days=WINDOW_DAYS - 1), today)
        batch = fetch(game, start, end)
        total_requests += 1
        for item in batch:
            iso = item["fecha_sorteo"][:10]
            numbers, comp, re_int = parse_combinacion(item.get("combinacion", ""))
            jackpot = (item.get("premio_bote") or "").strip()
            if game == "LAPR":
                if len(numbers) < 6:
                    continue
                draws[iso] = [f"{n:02d}" for n in numbers[:6]] + [
                    comp or "",
                    re_int or "",
                    jackpot,
                    parse_joker(item),
                ]
            else:
                if len(numbers) < 7:
                    continue
                draws[iso] = [f"{n:02d}" for n in numbers[:7]] + [jackpot]
        if len(batch) >= MAX_RESULTS:
            # window was truncated -> restart right before the oldest draw
            oldest = min(item["fecha_sorteo"][:10] for item in batch)
            nxt = dt.date.fromisoformat(oldest) - dt.timedelta(days=1)
            if nxt >= start:
                start = max(start, nxt - dt.timedelta(days=1))
                continue
        start = end + dt.timedelta(days=1)
        time.sleep(0.15)
    print(f"  {game}: {len(draws)} draws in {total_requests} requests")
    return draws


def write_csv(path: str, draws: dict) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        for iso in sorted(draws):
            fh.write(iso + ";" + ";".join(draws[iso]) + "\n")
    print(f"  wrote {path} ({len(draws)} rows)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="app/src/main/assets")
    parser.add_argument("--today", default=dt.date.today().isoformat())
    args = parser.parse_args()

    today = dt.date.fromisoformat(args.today)
    for game, (filename, first, _extra) in GAMES.items():
        print(f"Downloading {game} since {first} ...")
        draws = download_game(game, dt.date.fromisoformat(first), today)
        if draws:
            newest = max(draws)
            oldest = min(draws)
            print(f"  range {oldest} .. {newest}")
        write_csv(f"{args.out}/{filename}", draws)
    return 0


if __name__ == "__main__":
    sys.exit(main())
