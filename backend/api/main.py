"""
Synapse AI Backend API
FastAPI server for Remote Team Professional AI features
"""

from fastapi import FastAPI, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from firebase_admin import auth as firebase_auth
import os
from dotenv import load_dotenv

from routers import summarization, action_items, search, priority, decisions, agent, proactive
from version import API_VERSION

# Load environment variables
load_dotenv()

# Firebase is initialized in firebase_service.py

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events"""
    print("🚀 Synapse AI API starting...")
    yield
    print("👋 Synapse AI API shutting down...")

# Create FastAPI app
app = FastAPI(
    title="Synapse AI API",
    description="AI-powered features for Remote Team Professionals",
    version=API_VERSION,
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Auth dependency
async def verify_firebase_token(authorization: str = Header(None)):
    """Verify Firebase ID token from Android app"""
    if not authorization:
        raise HTTPException(status_code=401, detail="No authorization header")
    
    try:
        # Extract token from "Bearer <token>"
        token = authorization.split("Bearer ")[-1]
        decoded_token = firebase_auth.verify_id_token(token)
        return decoded_token["uid"]
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {str(e)}")

# Health check
@app.get("/")
async def root():
    return {
        "message": "Synapse AI API is running",
        "version": API_VERSION,
        "model": "gpt-3.5-turbo",
        "features": [
            "Thread Summarization",
            "Action Items Extraction",
            "Smart Semantic Search",
            "Priority Detection",
            "Decision Tracking",
            "Meeting Minutes Agent (Advanced)",
            "Proactive Assistant (Advanced Multi-Agent)"
        ]
    }

@app.get("/health")
async def health():
    return {"status": "healthy"}

# Include routers
app.include_router(summarization.router, prefix="/api", tags=["Summarization"])
app.include_router(action_items.router, prefix="/api", tags=["Action Items"])
app.include_router(search.router, prefix="/api", tags=["Smart Search"])
app.include_router(priority.router, prefix="/api", tags=["Priority Detection"])
app.include_router(decisions.router, prefix="/api", tags=["Decision Tracking"])
app.include_router(agent.router, prefix="/api", tags=["Advanced Agent"])
app.include_router(proactive.router, prefix="/api", tags=["Proactive Assistant"])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

