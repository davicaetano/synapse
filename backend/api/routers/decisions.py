"""
Decision Tracking API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import DecisionTrackingRequest, DecisionTrackingResponse
from services import firebase_service, openai_service
from datetime import datetime
import time

router = APIRouter()

@router.post("/decisions", response_model=DecisionTrackingResponse)
async def track_decisions(
    request: DecisionTrackingRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Track decisions made in conversation
    
    **Feature:** Decision Tracking for Remote Team Professionals
    - Never lose track of what was decided
    - See who agreed to what
    - Reference decisions later
    - Avoid re-discussing resolved topics
    """
    start_time = time.time()
    
    try:
        # Parse dates if provided
        start_date = datetime.fromisoformat(request.start_date) if request.start_date else None
        end_date = datetime.fromisoformat(request.end_date) if request.end_date else None
        
        # Fetch messages
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            start_date=start_date,
            end_date=end_date,
            max_messages=500
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        # Track decisions using OpenAI
        decisions = await openai_service.track_decisions(messages)
        
        # Sort by confidence (highest first)
        decisions.sort(key=lambda x: x.confidence, reverse=True)
        
        processing_time = int((time.time() - start_time) * 1000)
        
        return DecisionTrackingResponse(
            conversation_id=request.conversation_id,
            decisions=decisions,
            total_count=len(decisions),
            processing_time_ms=processing_time
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error tracking decisions: {str(e)}")

