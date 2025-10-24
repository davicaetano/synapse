"""
Firebase Firestore service for fetching conversation data
"""

import os
import firebase_admin
from firebase_admin import credentials, firestore
from typing import List, Optional
from datetime import datetime
from models.schemas import Message

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
        
        # Build query
        # Note: Not filtering by isDeleted here because old messages don't have this field
        # Android already filters deleted messages locally
        query = (messages_ref
                 .order_by('createdAtMs', direction=firestore.Query.DESCENDING)
                 .limit(max_messages))
        
        # Apply date filters if provided
        if start_date:
            query = query.where('createdAtMs', '>=', int(start_date.timestamp() * 1000))
        if end_date:
            query = query.where('createdAtMs', '<=', int(end_date.timestamp() * 1000))
        
        # Execute query
        docs = query.stream()
        
        messages = []
        for doc in docs:
            data = doc.to_dict()
            
            # Skip deleted messages
            if data.get('isDeleted', False):
                continue
            
            # Only analyze user text messages (ignore AI summaries, errors, bot messages)
            message_type = data.get('type', 'text')  # Default to 'text' for old messages without type
            if message_type != 'text':
                continue
            
            messages.append(Message(
                id=doc.id,
                text=data.get('text', ''),
                sender_id=data.get('senderId', ''),
                sender_name=data.get('senderName', 'Unknown'),
                created_at=datetime.fromtimestamp(data.get('createdAtMs', 0) / 1000),
                conversation_id=conversation_id
            ))
        
        # Reverse to get chronological order
        messages.reverse()
        
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
        return Message(
            id=doc.id,
            text=data.get('text', ''),
            sender_id=data.get('senderId', ''),
            sender_name=data.get('senderName', 'Unknown'),
            created_at=datetime.fromtimestamp(data.get('createdAtMs', 0) / 1000),
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
        import time
        from google.cloud.firestore import SERVER_TIMESTAMP
        
        # Create message document
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        message_ref = messages_ref.document()  # Auto-generate ID
        
        timestamp_ms = int(time.time() * 1000)
        
        message_data = {
            'id': message_ref.id,
            'text': summary_text,
            'senderId': 'synapse-bot-system',  # Bot ID for identification
            'createdAtMs': timestamp_ms,
            'memberIdsAtCreation': member_ids,
            'serverTimestamp': SERVER_TIMESTAMP,
            'type': 'ai_summary',  # AI summary message type (special rendering in Android)
            'isDeleted': False,
            'metadata': {
                'generatedBy': generated_by_user_id,
                'messageCount': message_count,
                'customInstructions': custom_instructions or '',
                'aiGenerated': True  # Mark as AI-generated for future features
            }
        }
        
        message_ref.set(message_data)
        
        # Update conversation metadata
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_ref.update({
            'lastMessageText': '🤖 AI Summary generated',
            'updatedAtMs': timestamp_ms
        })
        
        print(f"✅ Created AI summary message: {message_ref.id}")
        return message_ref.id
    
    except Exception as e:
        print(f"❌ Error creating AI summary message: {e}")
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
        import time
        from google.cloud.firestore import SERVER_TIMESTAMP
        
        SYNAPSE_BOT_ID = "synapse-bot-system"
        
        # Create message document
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        message_ref = messages_ref.document()  # Auto-generate ID
        
        timestamp_ms = int(time.time() * 1000)
        
        message_data = {
            'id': message_ref.id,
            'text': error_text,
            'senderId': SYNAPSE_BOT_ID,
            'createdAtMs': timestamp_ms,
            'memberIdsAtCreation': member_ids + [SYNAPSE_BOT_ID],
            'serverTimestamp': SERVER_TIMESTAMP,
            'type': 'ai_error',  # AI error message type (special rendering in Android)
            'isDeleted': False
        }
        
        message_ref.set(message_data)
        
        # Update conversation metadata
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_ref.update({
            'lastMessageText': '❌ AI Error',
            'lastMessageSenderId': SYNAPSE_BOT_ID,
            'lastMessageTimestamp': SERVER_TIMESTAMP,
            'updatedAt': SERVER_TIMESTAMP
        })
        
        print(f"✅ Created error message: {message_ref.id}")
        return message_ref.id
    
    except Exception as e:
        print(f"❌ Error creating error message: {e}")
        raise

