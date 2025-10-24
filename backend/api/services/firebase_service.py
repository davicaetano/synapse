"""
Firebase Firestore service for fetching conversation data
"""

from firebase_admin import firestore
from typing import List, Optional
from datetime import datetime
from models.schemas import Message

db = firestore.client()

async def get_conversation_messages(
    conversation_id: str,
    start_date: Optional[datetime] = None,
    end_date: Optional[datetime] = None,
    max_messages: int = 1000
) -> List[Message]:
    """
    Fetch messages from Firestore conversation
    """
    try:
        # Reference to messages subcollection
        messages_ref = db.collection('conversations').document(conversation_id).collection('messages')
        
        # Build query
        query = messages_ref.order_by('createdAtMs', direction=firestore.Query.DESCENDING).limit(max_messages)
        
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

