"""
Thread Summarization API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import SummarizeRequest, SummaryResponse
from services import firebase_service, openai_service
from datetime import datetime
import time

router = APIRouter()

@router.post("/summarize")
async def summarize_thread(
    request: SummarizeRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Generate a comprehensive summary and create AI message in Firestore
    
    **Feature:** Thread Summarization for Remote Team Professionals
    - Quickly catch up on long conversations
    - Get key points without reading everything
    - Perfect for async work across timezones
    - **NEW:** Creates AI summary message directly in the conversation
    """
    start_time = time.time()
    
    try:
        # Parse dates if provided
        start_date = datetime.fromisoformat(request.start_date) if request.start_date else None
        end_date = datetime.fromisoformat(request.end_date) if request.end_date else None
        
        # Fetch messages from Firestore (excluding deleted and AI summaries)
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
        member_ids = [p['id'] for p in participants]
        
        # Generate summary using OpenAI with custom instructions
        summary_data = await openai_service.summarize_thread(
            messages, 
            custom_instructions=request.custom_instructions
        )
        
        # Format summary text for message (with markdown formatting)
        summary_text = f"📊 **Thread Summary**\n\n{summary_data['summary']}\n\n**Key Points:**\n"
        for i, point in enumerate(summary_data['key_points'], 1):
            summary_text += f"{i}. {point}\n"
        
        summary_text += f"\n_({len(messages)} messages analyzed)_"
        
        # Create AI summary message in Firestore
        message_id = await firebase_service.create_ai_summary_message(
            conversation_id=request.conversation_id,
            summary_text=summary_text,
            generated_by_user_id=user_id,
            member_ids=member_ids,
            message_count=len(messages),
            custom_instructions=request.custom_instructions
        )
        
        processing_time = int((time.time() - start_time) * 1000)
        
        return {
            "success": True,
            "message_id": message_id,
            "conversation_id": request.conversation_id,
            "message_count": len(messages),
            "processing_time_ms": processing_time
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error generating summary: {str(e)}")


@router.post("/summarize/refine")
async def refine_summary(
    conversation_id: str,
    previous_summary_id: str,
    refinement_instructions: str,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Refine an existing AI summary with new instructions
    
    **Feature:** Iterative Summary Refinement
    - User can refine the summary with additional instructions
    - Creates a new AI summary message with the refined content
    - Preserves the previous summary for reference
    """
    start_time = time.time()
    
    try:
        # Fetch the previous summary message
        previous_summary = await firebase_service.get_message_by_id(conversation_id, previous_summary_id)
        
        if not previous_summary:
            raise HTTPException(status_code=404, detail="Previous summary not found")
        
        # Fetch all conversation messages (for context)
        messages = await firebase_service.get_conversation_messages(
            conversation_id=conversation_id,
            max_messages=1000
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        # Get participants
        participants = await firebase_service.get_conversation_participants(conversation_id)
        member_ids = [p['id'] for p in participants]
        
        # Build refinement instructions that include previous summary
        full_instructions = f"""The previous summary was:
---
{previous_summary.text}
---

User's refinement request: {refinement_instructions}

Please generate a NEW summary that addresses the user's feedback."""
        
        # Generate refined summary using OpenAI
        summary_data = await openai_service.summarize_thread(
            messages, 
            custom_instructions=full_instructions
        )
        
        # Format summary text for message
        summary_text = f"📊 **Thread Summary** (Refined)\n\n{summary_data['summary']}\n\n**Key Points:**\n"
        for i, point in enumerate(summary_data['key_points'], 1):
            summary_text += f"{i}. {point}\n"
        
        summary_text += f"\n_({len(messages)} messages analyzed • Refined from previous summary)_"
        
        # Create new AI summary message in Firestore
        message_id = await firebase_service.create_ai_summary_message(
            conversation_id=conversation_id,
            summary_text=summary_text,
            generated_by_user_id=user_id,
            member_ids=member_ids,
            message_count=len(messages),
            custom_instructions=refinement_instructions
        )
        
        processing_time = int((time.time() - start_time) * 1000)
        
        return {
            "success": True,
            "message_id": message_id,
            "conversation_id": conversation_id,
            "previous_summary_id": previous_summary_id,
            "message_count": len(messages),
            "processing_time_ms": processing_time
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error refining summary: {str(e)}")

