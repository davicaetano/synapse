"""
Priority Detection API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import PriorityDetectionRequest, PriorityDetectionResponse, PriorityMessage
from services import firebase_service, openai_service
import time

router = APIRouter()

@router.post("/priority", response_model=PriorityDetectionResponse)
async def detect_priority(
    request: PriorityDetectionRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Detect urgent and high-priority messages in conversation
    
    **Feature:** Priority Detection for Remote Team Professionals
    - Automatically surface urgent messages
    - Never miss critical deadlines
    - Identify blocking issues
    - Focus on what matters most
    """
    start_time = time.time()
    
    try:
        # Fetch messages
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            max_messages=100  # Analyze recent messages
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        # Detect priority messages using OpenAI
        priority_results = await openai_service.detect_priority(messages)
        
        # Build response with full message details
        priority_messages = []
        for result in priority_results:
            msg_id = result['message_id']
            message = next((m for m in messages if m.id == msg_id), None)
            
            if message:
                priority_messages.append(PriorityMessage(
                    message_id=message.id,
                    text=message.text,
                    sender_name=message.sender_name or "Unknown",
                    priority_score=result['priority_score'],
                    urgency_level=result['urgency_level'],
                    reasons=result['reasons'],
                    timestamp=message.created_at.isoformat()
                ))
        
        # Sort by priority score (highest first)
        priority_messages.sort(key=lambda x: x.priority_score, reverse=True)
        
        processing_time = int((time.time() - start_time) * 1000)
        
        return PriorityDetectionResponse(
            conversation_id=request.conversation_id,
            priority_messages=priority_messages,
            total_analyzed=len(messages),
            processing_time_ms=processing_time
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error detecting priority: {str(e)}")

