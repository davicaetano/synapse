"""
Smart Semantic Search API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import SearchRequest, SearchResponse, SearchResult
from services import firebase_service, openai_service
import time

router = APIRouter()

@router.post("/search", response_model=SearchResponse)
async def smart_search(
    request: SearchRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Perform intelligent semantic search on conversation
    
    **Feature:** Smart Search for Remote Team Professionals
    - Natural language queries ("What did we decide about the deadline?")
    - Context-aware results
    - Finds relevant messages even with different wording
    - No need to remember exact phrases
    """
    start_time = time.time()
    
    try:
        # Fetch all messages from conversation
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            max_messages=1000
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        # Perform semantic search using OpenAI
        search_results = await openai_service.semantic_search(
            query=request.query,
            messages=messages,
            max_results=request.max_results
        )
        
        # Build response with full message details
        results = []
        for result in search_results:
            # Find message in list
            msg_id = result['message_id']
            message = next((m for m in messages if m.id == msg_id), None)
            
            if message:
                results.append(SearchResult(
                    message_id=message.id,
                    text=message.text,
                    sender_name=message.sender_name or "Unknown",
                    timestamp=message.created_at.isoformat(),
                    relevance_score=result['relevance_score']
                ))
        
        processing_time = int((time.time() - start_time) * 1000)
        
        return SearchResponse(
            query=request.query,
            results=results,
            total_found=len(results),
            processing_time_ms=processing_time
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error performing search: {str(e)}")

