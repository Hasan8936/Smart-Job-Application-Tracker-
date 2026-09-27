import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import pandas as pd
import jobspy

app = FastAPI(title="JobSpy Service", version="1.2.0")

# Indeed/Glassdoor search one country at a time; JobSpy's own default is "usa".
DEFAULT_COUNTRY = os.getenv("JOBSPY_COUNTRY", "India")
# Identical searches within this window are answered from memory instead of hitting the job boards again.
CACHE_SECONDS = int(os.getenv("JOBSPY_CACHE_SECONDS", "600"))
MAX_QUERIES = 3
_cache = {}
_cache_lock = threading.Lock()


class SearchRequest(BaseModel):
    keywords: str = ""
    # Several searches (e.g. one per role) run in parallel and are merged; falls back to `keywords` when empty.
    queries: List[str] = []
    location: str = ""
    # Checked for India (Sep 2026): Naukri requires a reCAPTCHA (never bypassed), Glassdoor rejects the location,
    # Google returns nothing. LinkedIn and Indeed work.
    site_names: List[str] = ["linkedin", "indeed"]
    results_wanted: int = 20
    hours_old: Optional[int] = 168  # 1 week default
    country_indeed: str = DEFAULT_COUNTRY
    # LinkedIn only returns a description when asked (one extra request per job); without it the JD is empty.
    linkedin_fetch_description: bool = True


@app.post("/search")
def search_jobs(req: SearchRequest):
    queries = [q.strip() for q in (req.queries or []) if q and q.strip()][:MAX_QUERIES]
    if not queries:
        queries = [req.keywords.strip()]
    try:
        if len(queries) == 1:
            batches = [_search_cached(req, queries[0])]
        else:
            with ThreadPoolExecutor(max_workers=len(queries)) as pool:
                batches = list(pool.map(lambda q: _search_cached(req, q), queries))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    # Merge, dropping the same posting found by more than one search.
    seen, result = set(), []
    for batch in batches:
        for job in batch:
            key = job["externalId"] or job["applyUrl"]
            if key in seen:
                continue
            seen.add(key)
            result.append(job)
    return {"jobs": result}


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
        # HTML keeps paragraphs and lists; the backend normalizer turns it into clean plain text.
        description_format="html",
    )
    result = []
    for _, row in jobs_df.iterrows():
        result.append({
            "externalId": _str(row.get("id")),
            "title": _str(row.get("title")),
            "company": _str(row.get("company")),
            "location": _str(row.get("location")),
            "employmentType": _str(row.get("job_type")),
            "workMode": "remote" if row.get("is_remote") else "",
            "applyUrl": _str(row.get("job_url")),
            "postedAt": _str(row.get("date_posted")),
            "description": _str(row.get("description")),
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
    return {"status": "ok"}


@app.get("/health")
def health():
    return {"status": "ok"}
