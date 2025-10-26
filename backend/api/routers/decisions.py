"""
Decision Tracking API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import DecisionTrackingRequest
from services import firebase_service, openai_service
from version import API_VERSION
from datetime import datetime
import time

router = APIRouter()

@router.post("/decisions")
async def track_decisions(
    request: DecisionTrackingRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Track decisions made in conversation
    Creates an AI message in Firestore with decision summary
    
    **Feature:** Decision Tracking for Remote Team Professionals (Rubric Item 5)
    - Never lose track of what was decided
    - See who agreed to what
    - Reference decisions later
    - Avoid re-discussing resolved topics
    """
    start_time = time.time()
    
    try:
        # DEV: Force error for testing
        if request.conversation_id == "FORCE_ERROR":
            raise Exception("🧪 Forced error for testing!")
        
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
        
        print(f"📋 [DECISIONS] Analyzing {len(messages)} messages...")
        
        # Track decisions using OpenAI
        decisions = await openai_service.track_decisions(messages)
        
        # Sort by confidence (highest first)
        decisions.sort(key=lambda x: x.confidence, reverse=True)
        
        # Get participants
        participants = await firebase_service.get_conversation_participants(request.conversation_id)
        member_ids = [p['id'] for p in participants]
        
        # Calculate processing time
        processing_time = int((time.time() - start_time) * 1000)
        
        # Format decisions as text (ULTRA compact)
        if len(decisions) == 0:
            decisions_text = "📋 **Decision Tracking**\n\nNo decisions found."
        else:
            decisions_text = f"📋 **Decision Tracking** ({len(decisions)})\n\n"
            
            # TOP 3 only for speed
            for i, decision in enumerate(decisions[:3], 1):
                decisions_text += f"{i}. **{decision.decision}**\n"
                decisions_text += f"   👥 {', '.join(decision.decided_by)} • {decision.timestamp}\n\n"
        
        # Add metadata footer
        if request.dev_summary:
            decisions_text += f"\n_({len(messages)} messages analyzed • {len(decisions)} decisions • {processing_time}ms • API v{API_VERSION})_"
        
        # Create AI decisions message in Firestore (using ai_summary type for Android compatibility)
        message_id = await firebase_service.create_ai_message(
            conversation_id=request.conversation_id,
            text=decisions_text,
            message_type="ai_summary",  # Use ai_summary (Android supports this)
            member_ids=member_ids,
            send_notification=False,
            metadata={
                "generatedBy": user_id,
                "messageCount": len(messages),
                "decisionsCount": len(decisions),
                "aiGenerated": True,
                "feature": "decision_tracking"  # Track which AI feature generated this
            }
        )
        
        print(f"✅ [DECISIONS] Tracked {len(decisions)} decisions in {processing_time}ms")
        
        return {
            "success": True,
            "message_id": message_id,
            "conversation_id": request.conversation_id,
            "decisions_count": len(decisions),
            "processing_time_ms": processing_time
        }
    
    except Exception as e:
        # Write error message to Firestore
        try:
            participants = await firebase_service.get_conversation_participants(request.conversation_id)
            member_ids = [p['id'] for p in participants]
            
            error_text = f"""❌ **AI Error**

The decision tracking failed with the following error:

`{str(e)}`

Please try again or contact support if the issue persists."""
            
            await firebase_service.create_ai_message(
                conversation_id=request.conversation_id,
                text=error_text,
                message_type='ai_error',
                member_ids=member_ids,
                send_notification=True  # Errors need notifications
            )
        except Exception as firestore_error:
            print(f"Failed to write error message to Firestore: {firestore_error}")
        
        raise HTTPException(status_code=500, detail=f"Error tracking decisions: {str(e)}")

