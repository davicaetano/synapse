"""
Advanced AI Agent API - Meeting Minutes Generator
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import MeetingMinutesRequest, MeetingMinutesResponse
from services import firebase_service, agent_service
from datetime import datetime
import time

router = APIRouter()

@router.post("/meeting-minutes", response_model=MeetingMinutesResponse)
async def generate_meeting_minutes(
    request: MeetingMinutesRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Generate comprehensive meeting minutes using multi-step AI agent
    
    **Advanced AI Capability:** Autonomous Meeting Minutes Generator
    
    This agent:
    1. Analyzes entire conversation thread
    2. Generates executive summary
    3. Extracts all action items with assignments
    4. Identifies decisions made
    5. Determines next steps
    6. Formats professional document
    
    Perfect for:
    - End-of-day team sync
    - Project planning sessions
    - Decision-making discussions
    - Remote async meetings
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
        
        # Get participants
        participants = await firebase_service.get_conversation_participants(request.conversation_id)
        
        # Generate title if not provided
        title = request.title or f"Meeting Minutes - {messages[0].created_at.strftime('%B %d, %Y')}"
        
        # Run multi-step agent
        result = await agent_service.generate_meeting_minutes(
            messages=messages,
            conversation_id=request.conversation_id,
            title=title,
            participants=participants
        )
        
        processing_time = int((time.time() - start_time) * 1000)
        
        return MeetingMinutesResponse(
            conversation_id=request.conversation_id,
            processing_time_ms=processing_time,
            **result
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error generating meeting minutes: {str(e)}")

