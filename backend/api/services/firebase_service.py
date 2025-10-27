"""
Firebase Firestore service for fetching conversation data
"""

import os
import firebase_admin
from firebase_admin import credentials, firestore
from typing import List, Optional
from datetime import datetime
from models.schemas import Message
import asyncio

# Initialize Firebase Admin SDK (only once)
try:
    firebase_admin.get_app()
except ValueError:
    # Not initialized yet
    cred_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "./firebase-credentials.json")
    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)

db = firestore.client()

async def get_conversation_messages(
    conversation_id: str,
    start_date: Optional[datetime] = None,
    end_date: Optional[datetime] = None,
    max_messages: int = 1000
) -> List[Message]:
    """
    Fetch messages from Firestore conversation
    Filters out soft-deleted messages (isDeleted = true)
    """
    try:
        # Reference to messages subcollection
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        
        # Build query (fetch 100 messages, filter in Python, return 50 text messages)
        fetch_limit = 100  # Always fetch 100 to ensure we get 50+ text messages after filtering
        query = (messages_ref
                 .order_by('localTimestamp', direction=firestore.Query.DESCENDING)
                 .limit(fetch_limit))
        
        # Apply date filters if provided (convert to Timestamp for comparison)
        if start_date:
            query = query.where('localTimestamp', '>=', start_date)
        if end_date:
            query = query.where('localTimestamp', '<=', end_date)
        
        # Execute query
        docs = query.stream()
        
        # Collect ALL messages first, then filter
        all_messages = []
        for doc in docs:
            data = doc.to_dict()
            
            # Filter in Python (handles missing fields gracefully)
            # Skip deleted messages
            if data.get('isDeleted', False):
                continue
            
            # Include text messages AND ai_summary (for context awareness)
            # Skip other AI types (ai_error, ai_action_items, etc.)
            message_type = data.get('type', 'text')
            if message_type not in ['text', 'ai_summary']:
                continue
            
            # Get localTimestamp (Firestore Timestamp object)
            local_ts = data.get('localTimestamp')
            # DatetimeWithNanoseconds is already a datetime-like object
            created_at = local_ts if local_ts else datetime.fromtimestamp(0)
            
            all_messages.append(Message(
                id=doc.id,
                text=data.get('text', ''),
                sender_id=data.get('senderId', ''),
                sender_name=data.get('senderName', 'Unknown'),
                created_at=created_at,
                conversation_id=conversation_id
            ))
        
        # Take only max_messages (50) text messages
        messages = all_messages[:max_messages]
        
        # Reverse to get chronological order
        messages.reverse()
        
        print(f"📊 [FIREBASE] Fetched {len(messages)} text messages (Python-filtered from {fetch_limit} queried, {len(all_messages)} valid)")
        
        # Fetch user names in parallel for all unique sender IDs
        unique_sender_ids = list(set([msg.sender_id for msg in messages]))
        print(f"👥 [FIREBASE] Fetching names for {len(unique_sender_ids)} unique users in parallel...")
        
        # Helper function to fetch a single user (synchronous Firestore call)
        def fetch_user_name_sync(user_id: str) -> tuple[str, str]:
            try:
                user_doc = db.collection('users').document(user_id).get()
                if user_doc.exists:
                    user_data = user_doc.to_dict()
                    return (user_id, user_data.get('displayName', 'Unknown'))
                return (user_id, 'Unknown')
            except Exception as e:
                print(f"⚠️  Error fetching user {user_id}: {e}")
                return (user_id, 'Unknown')
        
        # Run synchronous Firestore calls in parallel threads using asyncio.to_thread
        user_results = await asyncio.gather(*[
            asyncio.to_thread(fetch_user_name_sync, uid) 
            for uid in unique_sender_ids
        ])
        
        # Create userId -> userName map
        user_map = dict(user_results)
        
        # Update sender_name for all messages
        for msg in messages:
            msg.sender_name = user_map.get(msg.sender_id, 'Unknown')
        
        print(f"✅ [FIREBASE] Updated {len(messages)} messages with user names")
        
        return messages
    
    except Exception as e:
        print(f"❌ Error fetching messages: {e}")
        raise

async def get_conversation_participants(conversation_id: str) -> List[dict]:
    """
    Get conversation participants with names
    """
    try:
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_doc = conv_ref.get()
        
        if not conv_doc.exists:
            return []
        
        data = conv_doc.to_dict()
        member_ids = data.get('memberIds', [])
        
        # Fetch user names
        participants = []
        for user_id in member_ids:
            user_ref = db.collection('users').document(user_id)
            user_doc = user_ref.get()
            
            if user_doc.exists:
                user_data = user_doc.to_dict()
                participants.append({
                    'id': user_id,
                    'name': user_data.get('displayName', 'Unknown'),
                    'email': user_data.get('email', '')
                })
        
        return participants
    
    except Exception as e:
        print(f"❌ Error fetching participants: {e}")
        return []

async def get_message_by_id(conversation_id: str, message_id: str) -> Optional[Message]:
    """
    Get a single message by ID
    """
    try:
        msg_ref = db.collection('conversations').document(conversation_id).collection('messages').document(message_id)
        doc = msg_ref.get()
        
        if not doc.exists:
            return None
        
        data = doc.to_dict()
        
        # Get localTimestamp (Firestore Timestamp object)
        local_ts = data.get('localTimestamp')
        # DatetimeWithNanoseconds is already a datetime-like object
        created_at = local_ts if local_ts else datetime.fromtimestamp(0)
        
        return Message(
            id=doc.id,
            text=data.get('text', ''),
            sender_id=data.get('senderId', ''),
            sender_name=data.get('senderName', 'Unknown'),
            created_at=created_at,
            conversation_id=conversation_id
        )
    
    except Exception as e:
        print(f"❌ Error fetching message: {e}")
        return None

async def create_ai_message(
    conversation_id: str,
    text: str,
    message_type: str,
    member_ids: List[str],
    send_notification: bool = False,
    metadata: Optional[dict] = None
) -> str:
    """
    ✨ UNIFIED method to create ANY type of AI message in Firestore (DRY principle)
    
    Consolidates: create_ai_summary_message, create_ai_message, create_error_message
    
    Supports all AI message types:
    - ai_summary: Thread summaries
    - ai_action_items: Extracted tasks
    - ai_priority: Urgent message detection
    - ai_decisions: Decision tracking
    - ai_error: Error messages
    
    Args:
        conversation_id: Conversation ID
        text: Message content (formatted markdown)
        message_type: One of: ai_summary, ai_action_items, ai_priority, ai_decisions, ai_error
        member_ids: List of active member IDs (bot will be added automatically)
        send_notification: Whether to send push notification (True for errors, False for AI analysis)
        metadata: Optional metadata dictionary
    
    Returns:
        Created message ID
    """
    try:
        from google.cloud.firestore import SERVER_TIMESTAMP
        from datetime import timedelta
        
        SYNAPSE_BOT_ID = "synapse-bot-system"
        
        # Get the last message's timestamp to ensure bot message appears AFTER
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        last_message_query = messages_ref.order_by('localTimestamp', direction='DESCENDING').limit(1).get()
        
        # Calculate timestamp: last message + 1 second (ensures bot message appears after user's message)
        if last_message_query:
            last_msg = last_message_query[0].to_dict()
            last_timestamp = last_msg.get('localTimestamp')
            if last_timestamp:
                # Add 1 second to last message timestamp
                bot_timestamp = last_timestamp + timedelta(seconds=1)
            else:
                bot_timestamp = SERVER_TIMESTAMP
        else:
            bot_timestamp = SERVER_TIMESTAMP
        
        # Create message document
        message_ref = messages_ref.document()  # Auto-generate ID
        
        message_data = {
            'id': message_ref.id,
            'text': text,
            'senderId': SYNAPSE_BOT_ID,
            'localTimestamp': bot_timestamp,  # Always AFTER last message
            'memberIdsAtCreation': member_ids + [SYNAPSE_BOT_ID],
            'serverTimestamp': SERVER_TIMESTAMP,  # Still use server timestamp for authoritative time
            'type': message_type,
            'sendNotification': send_notification,
            'isDeleted': False,
            'metadata': metadata or {}
        }
        
        message_ref.set(message_data)
        
        # Determine preview text based on message type
        preview_map = {
            'ai_summary': '📊 AI Summary',
            'ai_action_items': '📝 Action Items',
            'ai_priority': '🔥 Priority Alert',
            'ai_decisions': '📋 Decision Tracking',
            'ai_error': '❌ AI Error'
        }
        preview_text = preview_map.get(message_type, text[:100])
        
        # Update conversation metadata AND bot's lastMessageSentAt
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_ref.set({
            'lastMessageText': preview_text,
            'updatedAt': SERVER_TIMESTAMP,
            'members': {
                SYNAPSE_BOT_ID: {
                    'lastMessageSentAt': SERVER_TIMESTAMP
                }
            }
        }, merge=True)
        
        print(f"✅ [FIREBASE] Created AI message: type={message_type}, id={message_ref.id}, notify={send_notification}")
        return message_ref.id
    
    except Exception as e:
        print(f"❌ [FIREBASE] Error creating AI message: {e}")
        raise

