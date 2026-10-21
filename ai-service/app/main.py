from fastapi import FastAPI

app = FastAPI(
    title="DocuMind AI Internal Service",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
)


@app.get("/health", tags=["health"])
async def health() -> dict[str, str]:
    """Report process health without contacting Ollama or external services."""
    return {"status": "ok"}
