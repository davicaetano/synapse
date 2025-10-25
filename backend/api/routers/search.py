"""
Smart Semantic Search API (WhatsApp-style)
Returns only message IDs for in-conversation highlighting
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import SearchRequest
from services import firebase_service, openai_service
from version import API_VERSION
import time

router = APIRouter()

@router.post("/search")
async def smart_search(
    request: SearchRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Perform intelligent semantic search on conversation
    Returns message IDs for WhatsApp-style in-conversation navigation
    
    **Feature:** Smart Search for Remote Team Professionals (Rubric Item 3)
    - Natural language queries ("What did we decide about the deadline?")
    - AI-powered semantic understanding
    - Finds relevant messages even with different wording
    - Results shown in-conversation with navigation arrows
    """
    start_time = time.time()
    
    try:
        # Fetch all messages from conversation (limit 200 for search)
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            max_messages=200
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        print(f"🔍 [SEARCH] Query: '{request.query}' on {len(messages)} messages")
        
        # Perform semantic search using OpenAI
        search_results = await openai_service.semantic_search(
            query=request.query,
            messages=messages,
            max_results=request.max_results
        )
        
        # Extract only message IDs (WhatsApp-style)
        message_ids = [result['message_id'] for result in search_results]
        
        processing_time = int((time.time() - start_time) * 1000)
        
        print(f"✅ [SEARCH] Found {len(message_ids)} results in {processing_time}ms")
        
        return {
            "success": True,
            "conversation_id": request.conversation_id,
            "query": request.query,
            "message_ids": message_ids,
            "total_count": len(message_ids),
            "processing_time_ms": processing_time,
            "api_version": API_VERSION
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error performing search: {str(e)}")
