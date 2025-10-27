"""
Proactive Assistant Router
LangGraph-based multi-agent system for context-aware suggestions
"""

from fastapi import APIRouter, HTTPException
from models.schemas import ProactiveRequest, ProactiveResponse
from services import firebase_service, proactive_service
import time

router = APIRouter()

@router.post("/proactive", response_model=ProactiveResponse)
async def trigger_proactive_assistant(request: ProactiveRequest):
    """
    🤖 Proactive Assistant - Multi-Agent LangGraph System
    
    Analyzes conversation context and proactively suggests:
    - 🎬 Movies/entertainment (cinema context)
    - 🍽️ Restaurants/dining (food context)
    - 💡 Generic helpful suggestions
    
    Features:
    - Anti-spam (max 1 suggestion per 10 messages)
    - Activity check (conversation must be recent)
    - Context detection (LLM-based gatekeeper)
    - Specialized agents (Cinema/Restaurant/Generic)
    """
    start_time = time.time()
    
    try:
        print(f"🤖 [PROACTIVE] Request for conversation: {request.conversation_id}")
        
        # Fetch recent messages for context analysis
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            max_messages=20  # Last 20 messages for context
        )
        
        if not messages:
            print(f"⏸️  [PROACTIVE] No messages found")
            return ProactiveResponse(
                success=True,
                should_act=False,
                reason="no_messages"
            )
        
        # Run LangGraph multi-agent workflow
        result = await proactive_service.run_proactive_assistant(
            messages=messages,
            conversation_id=request.conversation_id
        )
        
        # If should act, create AI message in Firestore
        if result['should_act'] and result['suggestion_text']:
            # Get conversation participants
            participants = await firebase_service.get_conversation_participants(
                request.conversation_id
            )
            member_ids = [p['id'] for p in participants]
            
            # Create AI suggestion message
            message_id = await firebase_service.create_ai_message(
                conversation_id=request.conversation_id,
                text=result['suggestion_text'],
                message_type="ai_summary",  # Use existing type for Android compatibility
                member_ids=member_ids,
                send_notification=False,  # Silent suggestions
                metadata={
                    "feature": "proactive_assistant",
                    "context_type": result['context_type'],
                    "confidence": result['confidence'],
                    "aiGenerated": True
                }
            )
            
            processing_time = int((time.time() - start_time) * 1000)
            
            print(f"✅ [PROACTIVE] Suggestion sent: {result['context_type']} ({processing_time}ms)")
            
            return ProactiveResponse(
                success=True,
                should_act=True,
                context_type=result['context_type'],
                confidence=result['confidence'],
                message_id=message_id,
                processing_time_ms=processing_time
            )
        else:
            # No action needed (anti-spam, stale conversation, no context)
            processing_time = int((time.time() - start_time) * 1000)
            
            print(f"⏸️  [PROACTIVE] No action: {result['reason']} ({processing_time}ms)")
            
            return ProactiveResponse(
                success=True,
                should_act=False,
                reason=result['reason'],
                processing_time_ms=processing_time
            )
    
    except Exception as e:
        processing_time = int((time.time() - start_time) * 1000)
        
        print(f"❌ [PROACTIVE] Error: {str(e)}")
        
        return ProactiveResponse(
            success=False,
            should_act=False,
            reason=f"error: {str(e)}",
            processing_time_ms=processing_time
        )

