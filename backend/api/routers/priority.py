"""
Priority Detection API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import PriorityDetectionRequest
from services import firebase_service, openai_service
from version import API_VERSION
import time

router = APIRouter()

@router.post("/priority")
async def detect_priority(
    request: PriorityDetectionRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Detect urgent and high-priority messages in conversation
    Creates an AI message in Firestore with priority analysis
    
    **Feature:** Priority Detection for Remote Team Professionals (Rubric Item 4)
    - Automatically surface urgent messages
    - Never miss critical deadlines
    - Identify blocking issues
    - Focus on what matters most
    """
    start_time = time.time()
    
    try:
        # DEV: Force error for testing
        if request.conversation_id == "FORCE_ERROR":
            raise Exception("🧪 Forced error for testing!")
        
        # Fetch messages
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            max_messages=50  # Analyze recent 50 messages (optimized for speed)
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        print(f"🚨 [PRIORITY] Analyzing {len(messages)} messages...")
        
        # Detect priority messages using OpenAI
        priority_results = await openai_service.detect_priority(messages)
        
        # Get participants
        participants = await firebase_service.get_conversation_participants(request.conversation_id)
        member_ids = [p['id'] for p in participants]
        
        # Calculate processing time
        processing_time = int((time.time() - start_time) * 1000)
        
        # Format priority messages as text (ULTRA compact)
        if len(priority_results) == 0:
            priority_text = "🚨 **Priority Detection**\n\nNo urgent messages found."
        else:
            priority_text = f"🚨 **Priority Detection** ({len(priority_results)})\n\n"
            
            # TOP 3 only for speed
            for i, result in enumerate(priority_results[:3], 1):
                urgency_emoji = {
                    'urgent': '🔴',
                    'high': '🟠',
                    'medium': '🟡'
                }.get(result.get('urgency_level', 'medium'), '🟡')
                
                priority_text += f"{i}. {urgency_emoji} \"{result.get('message_text', 'N/A')}\"\n"
                priority_text += f"   👤 {result.get('sender_name', 'Unknown')} • {result.get('reason', 'urgent')}\n\n"
        
        # Add metadata footer
        if request.dev_summary:
            priority_text += f"\n_({len(messages)} messages analyzed • {len(priority_results)} priority • {processing_time}ms • API v{API_VERSION})_"
        
        # Create AI priority message in Firestore (using ai_summary type for Android compatibility)
        message_id = await firebase_service.create_ai_message(
            conversation_id=request.conversation_id,
            text=priority_text,
            message_type="ai_summary",  # Use ai_summary (Android supports this)
            member_ids=member_ids,
            send_notification=False,
            metadata={
                "generatedBy": user_id,
                "messageCount": len(messages),
                "priorityCount": len(priority_results),
                "aiGenerated": True,
                "feature": "priority_detection"  # Track which AI feature generated this
            }
        )
        
        print(f"✅ [PRIORITY] Detected {len(priority_results)} priority messages in {processing_time}ms")
        
        return {
            "success": True,
            "message_id": message_id,
            "conversation_id": request.conversation_id,
            "priority_count": len(priority_results),
            "total_analyzed": len(messages),
            "processing_time_ms": processing_time
        }
    
    except Exception as e:
        # Write error message to Firestore
        try:
            participants = await firebase_service.get_conversation_participants(request.conversation_id)
            member_ids = [p['id'] for p in participants]
            
            error_text = f"""❌ **AI Error**

The priority detection failed with the following error:

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
        
        raise HTTPException(status_code=500, detail=f"Error detecting priority: {str(e)}")

