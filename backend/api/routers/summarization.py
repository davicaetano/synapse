"""
Thread Summarization API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import SummarizeRequest, SummaryResponse
from services import firebase_service, openai_service
from datetime import datetime
import time

router = APIRouter()

@router.post("/summarize", response_model=SummaryResponse)
async def summarize_thread(
    request: SummarizeRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Generate a comprehensive summary of conversation thread
    
    **Feature:** Thread Summarization for Remote Team Professionals
    - Quickly catch up on long conversations
    - Get key points without reading everything
    - Perfect for async work across timezones
    """
    start_time = time.time()
    
    try:
        # Parse dates if provided
        start_date = datetime.fromisoformat(request.start_date) if request.start_date else None
        end_date = datetime.fromisoformat(request.end_date) if request.end_date else None
        
        # Fetch messages from Firestore
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            start_date=start_date,
            end_date=end_date,
            max_messages=request.max_messages
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        # Get participants
        participants = await firebase_service.get_conversation_participants(request.conversation_id)
        
        # Generate summary using OpenAI
        summary_data = await openai_service.summarize_thread(messages)
        
        # Build response
        processing_time = int((time.time() - start_time) * 1000)
        
        return SummaryResponse(
            conversation_id=request.conversation_id,
            summary=summary_data['summary'],
            key_points=summary_data['key_points'],
            participant_count=len(participants),
            message_count=len(messages),
            date_range=f"{messages[0].created_at.strftime('%Y-%m-%d')} to {messages[-1].created_at.strftime('%Y-%m-%d')}",
            processing_time_ms=processing_time
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error generating summary: {str(e)}")

