"""
Action Items Extraction API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import ActionItemsRequest, ActionItemsResponse
from services import firebase_service, openai_service
from datetime import datetime
import time

router = APIRouter()

@router.post("/action-items", response_model=ActionItemsResponse)
async def extract_action_items(
    request: ActionItemsRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Extract action items, tasks, and todos from conversation
    
    **Feature:** Action Items Extraction for Remote Team Professionals
    - Never miss a task or commitment
    - Automatic assignment tracking
    - Deadline detection
    - Priority classification
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
            end_date=end_date
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        # Extract action items using OpenAI
        action_items = await openai_service.extract_action_items(messages)
        
        # Build response
        processing_time = int((time.time() - start_time) * 1000)
        
        return ActionItemsResponse(
            conversation_id=request.conversation_id,
            action_items=action_items,
            total_count=len(action_items),
            processing_time_ms=processing_time
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error extracting action items: {str(e)}")

