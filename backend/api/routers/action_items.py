"""
Action Items Extraction API
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import ActionItemsRequest
from services import firebase_service, openai_service
from version import API_VERSION
from datetime import datetime
import time

router = APIRouter()

@router.post("/action-items")
async def extract_action_items(
    request: ActionItemsRequest,
    user_id: str = Depends(lambda: "mock_user")  # TODO: Add auth dependency
):
    """
    Extract action items, tasks, and todos from conversation
    Creates an AI message in Firestore with formatted action items
    
    **Feature:** Action Items Extraction for Remote Team Professionals
    - Never miss a task or commitment
    - Automatic assignment tracking
    - Deadline detection
    - Priority classification
    """
    start_time = time.time()
    
    try:
        # DEV: Force error for testing
        if request.custom_instructions == "FORCE_ERROR":
            raise Exception("🧪 Forced error for testing!")
        
        # Parse dates if provided
        start_date = datetime.fromisoformat(request.start_date) if request.start_date else None
        end_date = datetime.fromisoformat(request.end_date) if request.end_date else None
        
        # Fetch messages (limit to 50 for performance)
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            start_date=start_date,
            end_date=end_date,
            max_messages=50
        )
        
        if not messages:
            raise HTTPException(status_code=404, detail="No messages found")
        
        print(f"📝 [ACTION ITEMS] Processing {len(messages)} messages...")
        
        # Extract action items using OpenAI
        action_items = await openai_service.extract_action_items(messages)
        
        # Get participants
        participants = await firebase_service.get_conversation_participants(request.conversation_id)
        member_ids = [p['id'] for p in participants]
        
        # Calculate processing time
        processing_time = int((time.time() - start_time) * 1000)
        
        # Format action items as message text
        if len(action_items) == 0:
            action_items_text = "📝 **Action Items**\n\nNo action items found in this conversation."
        else:
            action_items_text = f"📝 **Action Items**\n\nFound {len(action_items)} action item(s):\n\n"
            
            for i, item in enumerate(action_items, 1):
                action_items_text += f"{i}. **{item.task}**\n"
                
                if item.assigned_to:
                    action_items_text += f"   👤 {item.assigned_to}\n"
                
                action_items_text += "\n"
        
        # Add metadata footer
        if request.dev_summary:
            action_items_text += f"\n_({len(messages)} messages analyzed • {processing_time}ms • API v{API_VERSION})_"
        else:
            action_items_text += f"\n_({len(messages)} messages analyzed)_"
        
        # Create AI action items message in Firestore
        message_id = await firebase_service.create_ai_message(
            conversation_id=request.conversation_id,
            text=action_items_text,
            message_type="ai_action_items",
            generated_by_user_id=user_id,
            member_ids=member_ids,
            metadata={
                "messageCount": len(messages),
                "actionItemsCount": len(action_items),
                "customInstructions": request.custom_instructions or "",
                "aiGenerated": True
            }
        )
        
        print(f"✅ [ACTION ITEMS] Extracted {len(action_items)} items in {processing_time}ms")
        
        return {
            "success": True,
            "message_id": message_id,
            "conversation_id": request.conversation_id,
            "action_items_count": len(action_items),
            "message_count": len(messages),
            "processing_time_ms": processing_time
        }
    
    except Exception as e:
        # Write error message to Firestore
        try:
            participants = await firebase_service.get_conversation_participants(request.conversation_id)
            member_ids = [p['id'] for p in participants]
            
            error_text = f"""❌ **AI Error**

The action items extraction failed with the following error:

`{str(e)}`

Please try again or contact support if the issue persists."""
            
            await firebase_service.create_error_message(
                conversation_id=request.conversation_id,
                error_text=error_text,
                member_ids=member_ids
            )
        except Exception as firestore_error:
            print(f"Failed to write error message to Firestore: {firestore_error}")
        
        raise HTTPException(status_code=500, detail=f"Error extracting action items: {str(e)}")

