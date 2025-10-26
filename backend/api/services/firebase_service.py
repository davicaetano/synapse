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
            
            # Only analyze user text messages (ignore AI summaries, errors, bot messages)
            message_type = data.get('type', 'text')
            if message_type != 'text':
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

async def create_ai_summary_message(
    conversation_id: str,
    summary_text: str,
    generated_by_user_id: str,
    member_ids: List[str],
    message_count: int,
    custom_instructions: Optional[str] = None
) -> str:
    """
    Create an AI summary message in Firestore
    Returns the created message ID
    """
    try:
        from google.cloud.firestore import SERVER_TIMESTAMP
        
        # Create message document
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        message_ref = messages_ref.document()  # Auto-generate ID
        
        message_data = {
            'id': message_ref.id,
            'text': summary_text,
            'senderId': 'synapse-bot-system',  # Bot ID for identification
            'localTimestamp': SERVER_TIMESTAMP,  # Use server timestamp for both fields
            'memberIdsAtCreation': member_ids,
            'serverTimestamp': SERVER_TIMESTAMP,
            'type': 'ai_summary',  # AI summary message type (special rendering in Android)
            'sendNotification': False,  # Don't send push notification for summaries
            'isDeleted': False,
            'metadata': {
                'generatedBy': generated_by_user_id,
                'messageCount': message_count,
                'customInstructions': custom_instructions or '',
                'aiGenerated': True  # Mark as AI-generated for future features
            }
        }
        
        message_ref.set(message_data)
        
        # Update conversation metadata AND bot's lastMessageSentAt (merge to create if not exists)
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_ref.set({
            'lastMessageText': '🤖 AI Summary generated',
            'updatedAt': SERVER_TIMESTAMP,  # Using serverTimestamp for updatedAt
            'members': {
                'synapse-bot-system': {
                    'lastMessageSentAt': SERVER_TIMESTAMP
                }
            }
        }, merge=True)
        
        print(f"✅ Created AI summary message: {message_ref.id}")
        return message_ref.id
    
    except Exception as e:
        print(f"❌ Error creating AI summary message: {e}")
        raise

async def create_ai_message(
    conversation_id: str,
    text: str,
    message_type: str,
    generated_by_user_id: str,
    member_ids: List[str],
    metadata: dict = None
) -> str:
    """
    Generic method to create any type of AI message in Firestore
    Supports: ai_summary, ai_action_items, ai_decisions, ai_priority, etc.
    Returns the created message ID
    """
    try:
        from google.cloud.firestore import SERVER_TIMESTAMP
        
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        message_ref = messages_ref.document()
        
        message_data = {
            'id': message_ref.id,
            'text': text,
            'senderId': 'synapse-bot-system',
            'localTimestamp': SERVER_TIMESTAMP,  # Use server timestamp for both fields
            'memberIdsAtCreation': member_ids,
            'serverTimestamp': SERVER_TIMESTAMP,
            'type': message_type,
            'sendNotification': False,  # Don't send push notifications for AI messages
            'isDeleted': False,
            'metadata': metadata or {}
        }
        
        message_ref.set(message_data)
        
        # Update conversation's lastMessageText AND bot's lastMessageSentAt (merge to create if not exists)
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_ref.set({
            'lastMessageText': text[:100],
            'updatedAt': SERVER_TIMESTAMP,  # Using serverTimestamp for updatedAt
            'members': {
                'synapse-bot-system': {
                    'lastMessageSentAt': SERVER_TIMESTAMP
                }
            }
        }, merge=True)
        
        print(f"✅ Created AI message: type={message_type}, id={message_ref.id}")
        return message_ref.id
    
    except Exception as e:
        print(f"❌ Error creating AI message: {e}")
        raise

async def create_error_message(
    conversation_id: str,
    error_text: str,
    member_ids: List[str]
) -> str:
    """
    Create an error message in Firestore (sent by Synapse Bot)
    Returns the created message ID
    """
    try:
        from google.cloud.firestore import SERVER_TIMESTAMP
        
        SYNAPSE_BOT_ID = "synapse-bot-system"
        
        # Create message document
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        message_ref = messages_ref.document()  # Auto-generate ID
        
        message_data = {
            'id': message_ref.id,
            'text': error_text,
            'senderId': SYNAPSE_BOT_ID,
            'localTimestamp': SERVER_TIMESTAMP,  # Use server timestamp for both fields
            'memberIdsAtCreation': member_ids + [SYNAPSE_BOT_ID],
            'serverTimestamp': SERVER_TIMESTAMP,
            'type': 'ai_error',  # AI error message type (special rendering in Android)
            'sendNotification': True,  # Send notification for errors (user needs to know)
            'isDeleted': False
        }
        
        message_ref.set(message_data)
        
        # Update conversation metadata AND bot's lastMessageSentAt (merge to create if not exists)
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_ref.set({
            'lastMessageText': '❌ AI Error',
            'updatedAt': SERVER_TIMESTAMP,  # Using serverTimestamp for updatedAt
            'members': {
                SYNAPSE_BOT_ID: {
                    'lastMessageSentAt': SERVER_TIMESTAMP
                }
            }
        }, merge=True)
        
        print(f"✅ Created error message: {message_ref.id}")
        return message_ref.id
    
    except Exception as e:
        print(f"❌ Error creating error message: {e}")
        raise

