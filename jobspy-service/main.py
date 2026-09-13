from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import pandas as pd
import jobspy

app = FastAPI(title="JobSpy Service", version="1.0.0")


class SearchRequest(BaseModel):
    keywords: str
    location: str = ""
    site_names: List[str] = ["linkedin", "indeed", "glassdoor", "google"]
    results_wanted: int = 20
    hours_old: Optional[int] = 168  # 1 week default


@app.post("/search")
def search_jobs(req: SearchRequest):
    try:
        jobs_df = jobspy.scrape_jobs(
            site_name=req.site_names,
            search_term=req.keywords,
            location=req.location if req.location else None,
            results_wanted=req.results_wanted,
            hours_old=req.hours_old,
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
                "source": _str(row.get("site")),
            })
        return {"jobs": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


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
