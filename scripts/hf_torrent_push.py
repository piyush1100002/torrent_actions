import base64
import os
import re
import sys
import time
from urllib.parse import parse_qs, unquote, urlsplit
from pathlib import Path

import pandas as pd
import requests
from guessit import guessit
from huggingface_hub import HfApi
from sqlalchemy import create_engine, text

VIDEO_EXTENSIONS = {".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".mpg", ".mpeg"}
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}


def clean_title(value: str) -> str:
    if not value:
        return ""
    cleaned = value.strip()
    cleaned = re.sub(r"[._-]+", " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned)
    cleaned = re.sub(r"[^a-zA-Z0-9\s]", "", cleaned)
    return cleaned.strip().lower()


def title_variants(title: str):
    variants = []
    seen = set()
    for candidate in [title, title.strip(" ._-")]:
        if not candidate:
            continue
        normalized = re.sub(r"\s+", " ", candidate).strip()
        if normalized and normalized not in seen:
            variants.append(normalized)
            seen.add(normalized)
    return variants


def digits_match(target: str, candidate: str) -> bool:
    target_digits = re.findall(r"\d+", target)
    if not target_digits:
        return True
    candidate_digits = re.findall(r"\d+", candidate)
    return target_digits == candidate_digits


def extract_infohash(value: str) -> str:
    candidate = (value or "").strip()
    if not candidate:
        return ""

    match = re.search(r"btih:([0-9a-fA-F]{40}|[A-Z2-7]{32})", candidate, re.IGNORECASE)
    if match:
        digest = match.group(1).lower()
        if len(digest) == 32:
            pad = "=" * ((8 - len(digest) % 8) % 8)
            try:
                digest = base64.b32decode(digest + pad).hex()
            except Exception:
                pass
        return digest

    if re.fullmatch(r"[0-9a-fA-F]{40}", candidate):
        return candidate.lower()

    if re.fullmatch(r"[A-Z2-7]{32}", candidate):
        pad = "=" * ((8 - len(candidate) % 8) % 8)
        try:
            return base64.b32decode(candidate + pad).hex()
        except Exception:
            return candidate.lower()

    return ""


def extract_torrent_title(value: str) -> str:
    source = (value or "").strip()
    if source.lower().startswith("magnet:"):
        query = parse_qs(urlsplit(source).query)
        display_name = query.get("dn", [""])[0]
        if display_name:
            return unquote(display_name).strip()
    return source


def resolve_imdb_id(title: str, year: int | None = None, is_series: bool = False) -> str:
    query = (title or "").strip()
    if not query:
        return ""

    variants = title_variants(query)
    for candidate in variants:
        cleaned = clean_title(candidate)
        if not cleaned:
            continue

        first = cleaned[0] if cleaned[0].isalnum() else "x"
        encoded = requests.utils.quote(candidate, safe="")
        url = f"https://v3.sg.media-imdb.com/suggestion/{first}/{encoded}.json"
        for attempt in range(1, 4):
            try:
                response = requests.get(url, headers=HEADERS, timeout=20)
                if response.status_code != 200:
                    continue
                payload = response.json()
                results = payload.get("d", [])
                if not results:
                    continue

                best = None
                best_score = -9999
                for idx, result in enumerate(results):
                    imdb_id = result.get("id", "")
                    if not imdb_id or not imdb_id.startswith("tt"):
                        continue

                    result_title = result.get("l", "")
                    result_year = result.get("y")
                    result_kind = str(result.get("q", "")).lower()
                    result_clean = clean_title(result_title)

                    if not digits_match(cleaned, result_clean):
                        continue

                    score = 0
                    if cleaned == result_clean:
                        score += 150
                    elif cleaned in result_clean or result_clean in cleaned:
                        score += 50

                    if year is not None and result_year is not None:
                        diff = abs(int(year) - int(result_year))
                        if diff == 0:
                            score += 80
                        elif diff == 1:
                            score += 20
                        else:
                            score -= 200

                    is_result_series = "series" in result_kind or "mini-series" in result_kind
                    if is_series == is_result_series:
                        score += 30
                    else:
                        score -= 50

                    score -= idx * 3
                    if score > best_score:
                        best_score = score
                        best = imdb_id

                if best and best_score > 0:
                    return best
            except Exception:
                if attempt == 3:
                    continue

    return ""


def build_imdb_key(imdb_id: str | None, is_series: bool, season: int | None, episode: int | None) -> str:
    imdb_value = (imdb_id or "").strip()
    if not imdb_value:
        return ""
    if is_series and season is not None and episode is not None:
        return f"{imdb_value}:{season}:{episode}"
    return imdb_value


def parse_torrent_metadata(torrent_name: str, file_name: str, imdb_override: str | None = None):
    torrent_name = torrent_name or ""
    file_name = file_name or ""
    torrent_title = extract_torrent_title(torrent_name)

    parsed_torrent = guessit(torrent_title)
    parsed_file = guessit(file_name)

    title = parsed_torrent.get("title") or parsed_file.get("title") or torrent_title
    year = parsed_torrent.get("year") or parsed_file.get("year")
    season = parsed_torrent.get("season") or parsed_file.get("season")
    episode = parsed_torrent.get("episode") or parsed_file.get("episode")

    if season is None or episode is None:
        match = re.search(r"[Ss](\d{1,2})[Ee](\d{1,2})", file_name)
        if match:
            season = season if season is not None else int(match.group(1))
            episode = episode if episode is not None else int(match.group(2))

    is_series = bool(parsed_torrent.get("type") == "episode" or parsed_file.get("type") == "episode" or season is not None or episode is not None)
    if isinstance(title, (list, tuple)):
        title = title[0]

    imdb_id = (imdb_override or "").strip()
    if not imdb_id:
        imdb_id = resolve_imdb_id(str(title), int(year) if year is not None else None, is_series=is_series)
        if not imdb_id and title:
            imdb_id = resolve_imdb_id(str(title), None, is_series=is_series)

    return {
        "title": str(title),
        "year": int(year) if year is not None else None,
        "season": int(season) if season is not None else None,
        "episode": int(episode) if episode is not None else None,
        "is_series": is_series,
        "imdb_id": imdb_id,
    }


def find_video_files(root_dir: Path):
    for path in sorted(root_dir.rglob("*")):
        if not path.is_file():
            continue
        if path.suffix.lower() not in VIDEO_EXTENSIONS:
            continue
        if "sample" in path.name.lower():
            continue
        if "trailer" in path.name.lower() or "teaser" in path.name.lower() or "preview" in path.name.lower():
            continue
        yield path


def ensure_hftor_table(engine) -> None:
    with engine.begin() as conn:
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS hftor (
                    imdb_id TEXT,
                    name TEXT,
                    file_name TEXT,
                    url TEXT,
                    size BIGINT,
                    time DOUBLE PRECISION,
                    hash TEXT
                )
                """
            )
        )


def upload_single_file(file_path: Path, repo_id: str, token: str, torrent_name: str, database_url: str | None = None, engine=None, imdb_override: str | None = None, file_index: int = 1):
    api = HfApi(token=token)
    torrent_title = extract_torrent_title(torrent_name)
    metadata = parse_torrent_metadata(torrent_name, file_path.name, imdb_override=imdb_override)
    imdb_id = metadata["imdb_id"]
    imdb_key = build_imdb_key(imdb_id, metadata["is_series"], metadata["season"], metadata["episode"])
    if not imdb_key:
        raise RuntimeError(f"Could not resolve IMDb ID for torrent: {extract_torrent_title(torrent_name)}")
    infohash = extract_infohash(torrent_name) or f"hf-{file_path.stem}-{abs(hash(file_path.resolve()))}"

    remote_name = f"{infohash}_{file_index}"

    upload_error = None
    for attempt in range(1, 4):
        try:
            print(f"Uploading {file_path.name} ({attempt}/3)")
            api.upload_file(
                path_or_fileobj=str(file_path),
                path_in_repo=remote_name,
                repo_id=repo_id,
                repo_type="dataset",
            )
            upload_error = None
            break
        except Exception as exc:
            upload_error = exc
            if attempt < 3:
                print(f"Upload attempt {attempt} failed; retrying...")
                time.sleep(10 * attempt)

    if upload_error is not None:
        raise upload_error

    row = {
        "imdb_id": imdb_key,
        "name": torrent_title or file_path.name,
        "file_name": file_path.name,
        "url": f"https://huggingface.co/datasets/{repo_id}/resolve/main/{remote_name}?download=true",
        "size": file_path.stat().st_size,
        "time": time.time(),
        "hash": infohash,
    }
    print(f"Resolved IMDb key: {imdb_key}")

    if database_url and engine is not None:
        try:
            pd.DataFrame([row]).to_sql(name="hftor", con=engine, if_exists="append", index=False)
            print(f"Inserted metadata row for {file_path.name} into Postgres")
        except Exception as exc:
            print(f"Postgres insert failed for {file_path.name}: {exc}")

    return {
        "file": file_path.name,
        "imdb_id": imdb_key,
        "remote_name": remote_name,
        "metadata": metadata,
        "row": row,
    }


def main():
    imdb_override = os.environ.get("IMDB_ID", "").strip()
    torrent_dir = Path(os.environ.get("TORRENT_DIR", f"./{imdb_override or 'hf_dataset_input'}")).resolve()
    repo_id = os.environ.get("HF_REPO_ID")
    token = os.environ.get("HF_TOKEN")
    torrent_name = os.environ.get("TORRENT_NAME", torrent_dir.name)
    database_url = os.environ.get("POSTGRES_URL")

    if not repo_id:
        raise SystemExit("HF_REPO_ID is required. Set it to your Hugging Face dataset repo, for example: your-user/your-dataset")
    if not token:
        raise SystemExit("HF_TOKEN is required before uploading to Hugging Face.")

    if not torrent_dir.exists():
        raise SystemExit(f"Directory not found: {torrent_dir}")

    engine = None
    if database_url:
        engine = create_engine(database_url)
        ensure_hftor_table(engine)

    uploaded = []
    file_paths = list(find_video_files(torrent_dir))
    for index, file_path in enumerate(file_paths, start=1):
        uploaded.append(
            upload_single_file(
                file_path=file_path,
                repo_id=repo_id,
                token=token,
                torrent_name=torrent_name,
                database_url=database_url,
                engine=engine,
                imdb_override=imdb_override,
                file_index=index,
            )
        )

    print(f"Uploaded {len(uploaded)} files to Hugging Face dataset repo: {repo_id}")
    for item in uploaded:
        print(item["row"])


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # pragma: no cover
        print(f"HF upload failed: {exc}", file=sys.stderr)
        raise
