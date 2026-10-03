import os
import threading
import time

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
import pandas as pd
import jobspy

app = FastAPI(title="JobSpy Service", version="1.3.0")

DEFAULT_COUNTRY = os.getenv("JOBSPY_COUNTRY", "India")
CACHE_SECONDS = int(os.getenv("JOBSPY_CACHE_SECONDS", "600"))
MAX_QUERIES = int(os.getenv("JOBSPY_MAX_QUERIES", "3"))
MAX_RESULTS = int(os.getenv("JOBSPY_MAX_RESULTS", "30"))
DESCRIPTION_LIMIT = int(os.getenv("JOBSPY_DESCRIPTION_LIMIT", "12000"))
# Render's smallest instances cannot run several browser-like scrapes at once.
# A second request receives 429 and the Java client retries it instead of exhausting RAM.
_SEARCH_SLOT = threading.BoundedSemaphore(1)
_cache = {}
_cache_lock = threading.Lock()


class SearchRequest(BaseModel):
    keywords: str = Field(default="", max_length=200)
    queries: List[str] = Field(default_factory=list, max_length=MAX_QUERIES)
    location: str = Field(default="", max_length=120)
    site_names: List[str] = Field(default_factory=lambda: ["linkedin", "indeed"], max_length=4)
    results_wanted: int = Field(default=20, ge=1, le=MAX_RESULTS)
    hours_old: Optional[int] = Field(default=168, ge=1, le=720)
    country_indeed: str = Field(default=DEFAULT_COUNTRY, max_length=80)
    # LinkedIn description fetches add one extra request per job and are disabled by default.
    linkedin_fetch_description: bool = False


@app.post("/search")
def search_jobs(req: SearchRequest):
    if not _SEARCH_SLOT.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="JobSpy worker is busy; retry shortly")
    try:
        queries = [q.strip() for q in (req.queries or []) if q and q.strip()][:MAX_QUERIES]
        if not queries:
            queries = [req.keywords.strip()]
        # Keep one scrape active at a time. The Java API queue serializes bursts before they reach here.
        batches = [_search_cached(req, query) for query in queries]
        seen, result = set(), []
        for batch in batches:
            for job in batch:
                key = job["externalId"] or job["applyUrl"]
                if key in seen:
                    continue
                seen.add(key)
                result.append(job)
        return {"jobs": result[: min(MAX_RESULTS, req.results_wanted * max(1, len(queries)))]}
    finally:
        _SEARCH_SLOT.release()


def _search_cached(req: SearchRequest, query: str):
    key = (query.lower(), req.location, tuple(req.site_names), req.results_wanted, req.hours_old,
           req.country_indeed, req.linkedin_fetch_description)
    now = time.monotonic()
    with _cache_lock:
        hit = _cache.get(key)
        if hit and now - hit[0] < CACHE_SECONDS:
            return hit[1]
    jobs = _search(req, query)
    with _cache_lock:
        for k in [k for k, v in _cache.items() if now - v[0] >= CACHE_SECONDS]:
            del _cache[k]
        _cache[key] = (now, jobs)
    return jobs


def _search(req: SearchRequest, query: str):
    jobs_df = jobspy.scrape_jobs(
        site_name=req.site_names,
        search_term=query,
        location=req.location if req.location else None,
        results_wanted=req.results_wanted,
        hours_old=req.hours_old,
        country_indeed=req.country_indeed,
        linkedin_fetch_description=req.linkedin_fetch_description,
        description_format="html",
    )
    result = []
    for _, row in jobs_df.iterrows():
        description = _str(row.get("description"))
        result.append({
            "externalId": _str(row.get("id")),
            "title": _str(row.get("title")),
            "company": _str(row.get("company")),
            "location": _str(row.get("location")),
            "employmentType": _str(row.get("job_type")),
            "workMode": "remote" if row.get("is_remote") else "",
            "applyUrl": _str(row.get("job_url")),
            "postedAt": _str(row.get("date_posted")),
            "description": description[:DESCRIPTION_LIMIT],
            "salaryMin": _num(row.get("min_amount")),
            "salaryMax": _num(row.get("max_amount")),
            "salaryCurrency": _str(row.get("currency")),
            "salaryPeriod": _str(row.get("interval")),
            "source": _str(row.get("site")),
        })
    return result


def _str(val) -> str:
    if val is None:
        return ""
    try:
        if pd.isna(val):
            return ""
    except (TypeError, ValueError):
        pass
    return str(val)


def _num(val) -> Optional[float]:
    if val is None:
        return None
    try:
        if pd.isna(val):
            return None
        return float(val)
    except (TypeError, ValueError):
        return None


@app.get("/")
def root():
    return {"status": "ok", "service": "jobspy"}


@app.get("/health")
def health():
    return {"status": "ok", "service": "jobspy", "cacheEntries": len(_cache)}
