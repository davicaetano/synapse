"""
Thread Summarization API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import SummarizeRequest, SummaryResponse
from services import firebase_service, openai_service  # , rag_service  # ❌ RAG disabled for performance
from version import API_VERSION
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
        # DEV: Force error for testing (triggered by Dev Settings toggle)
        if request.custom_instructions == "FORCE_ERROR":
            raise Exception("🧪 Forced error for testing! This was triggered by the 'Force Error' dev setting.")
        
        # Parse dates if provided
        start_date = datetime.fromisoformat(request.start_date) if request.start_date else None
        end_date = datetime.fromisoformat(request.end_date) if request.end_date else None
        
        # Fetch messages from Firestore (excluding deleted and AI summaries)
        # Limit to 50 most recent messages for faster processing
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            start_date=start_date,
            end_date=end_date,
            max_messages=min(request.max_messages, 50)  # Cap at 50 for performance
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        # ❌ RAG DISABLED FOR PERFORMANCE (scikit-learn too heavy for Render free tier)
        # initial_count = len(messages)
        # 
        # # Apply RAG filtering if we have many messages (removes redundancy)
        # # Only apply if >30 messages to make it worthwhile
        # if len(messages) > 30:
        #     print(f"🔥 [SUMMARIZATION] Applying RAG filter to {len(messages)} messages...")
        #     messages = await rag_service.filter_redundant_messages(
        #         messages,
        #         target_count=25  # Target ~25 unique messages
        #     )
        #     print(f"🔥 [SUMMARIZATION] RAG filter: {initial_count} → {len(messages)} messages")
        # else:
        #     print(f"ℹ️  [SUMMARIZATION] Skipping RAG ({len(messages)} ≤ 30 messages)")
        
        print(f"📊 [SUMMARIZATION] Processing {len(messages)} messages (RAG disabled)")
        
        # Get participants
        participants = await firebase_service.get_conversation_participants(request.conversation_id)
        member_ids = [p['id'] for p in participants]
        
        # Generate summary using OpenAI with custom instructions
        summary_data = await openai_service.summarize_thread(
            messages, 
            custom_instructions=request.custom_instructions
        )
        
        # Calculate processing time
        processing_time = int((time.time() - start_time) * 1000)
        
        # Format summary text for message (with markdown formatting)
        summary_text = f"📊 **Thread Summary**\n\n{summary_data['summary']}\n\n**Key Points:**\n"
        for i, point in enumerate(summary_data['key_points'], 1):
            summary_text += f"{i}. {point}\n"
        
        # Add message count and optionally dev info (processing time + API version)
        if request.dev_summary:
            summary_text += f"\n_({len(messages)} messages analyzed • {processing_time}ms • API v{API_VERSION})_"
        else:
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
        
        return {
            "success": True,
            "message_id": message_id,
            "conversation_id": request.conversation_id,
            "message_count": len(messages),
            "processing_time_ms": processing_time
        }
    
    except Exception as e:
        # Write error message to Firestore (visible to all users)
        try:
            participants = await firebase_service.get_conversation_participants(request.conversation_id)
            member_ids = [p['id'] for p in participants]
            
            error_text = f"""❌ **AI Error**

The AI processing failed with the following error:

`{str(e)}`

Please try again or contact support if the issue persists."""
            
            # Create error message in Firestore (sent by Synapse Bot)
            await firebase_service.create_error_message(
                conversation_id=request.conversation_id,
                error_text=error_text,
                member_ids=member_ids
            )
        except Exception as firestore_error:
            # If Firestore write fails, just log it
            print(f"Failed to write error message to Firestore: {firestore_error}")
        
        # Still return HTTP error for Android to log
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

