"""
Firebase Cloud Functions for Synapse - Push Notifications

This module handles sending push notifications when new messages are created.
Uses Firebase 2nd gen Cloud Functions with Python.
"""

from firebase_functions import firestore_fn, options
from firebase_admin import initialize_app, messaging, firestore as admin_firestore
import logging

# Initialize Firebase Admin SDK
initialize_app()

logger = logging.getLogger(__name__)


@firestore_fn.on_document_created(
    document="conversations/{conversation_id}/messages/{message_id}",
    region="us-central1",
    memory=options.MemoryOption.MB_256,
    timeout_sec=60
)
def send_notification_on_new_message(event: firestore_fn.Event[firestore_fn.DocumentSnapshot]):
    """
    Send push notifications when a new message is created.
    
    Triggered by: Firestore onCreate at conversations/{convId}/messages/{msgId}
    
    Flow:
    1. Get message data and conversation
    2. Find all recipients (members except sender)
    3. Get FCM tokens for recipients
    4. Send multicast notification
    5. Clean up invalid tokens
    """
    try:
        # Get message data
        message_data = event.data.to_dict()
        conversation_id = event.params['conversation_id']
        message_id = event.params['message_id']
        
        if not message_data:
            logger.warning(f"Message {message_id} has no data")
            return
        
        sender_id = message_data.get('senderId')
        message_text = message_data.get('text', '')
        
        logger.info(f"New message {message_id} from {sender_id} in conversation {conversation_id}")
        
        # Get Firestore client
        db = admin_firestore.client()
        
        # Get conversation document
        conv_ref = db.collection('conversations').document(conversation_id)
        conv_doc = conv_ref.get()
        
        if not conv_doc.exists:
            logger.warning(f"Conversation {conversation_id} not found")
            return
        
        conversation = conv_doc.to_dict()
        member_ids = conversation.get('memberIds', [])
        conv_type = conversation.get('convType', 'DIRECT')
        
        # Get sender's name
        sender_ref = db.collection('users').document(sender_id)
        sender_doc = sender_ref.get()
        sender_name = sender_doc.to_dict().get('displayName', 'Someone') if sender_doc.exists else 'Someone'
        
        # Find recipients (all members except sender)
        recipient_ids = [uid for uid in member_ids if uid != sender_id]
        
        if not recipient_ids:
            logger.info("No recipients to notify")
            return
        
        # Collect FCM tokens from all recipients
        tokens = []
        for user_id in recipient_ids:
            tokens_ref = db.collection('users').document(user_id).collection('fcmTokens')
            tokens_snapshot = tokens_ref.stream()
            
            for token_doc in tokens_snapshot:
                tokens.append(token_doc.id)  # Token is the document ID
        
        if not tokens:
            logger.info("No FCM tokens found for recipients")
            return
        
        # Build notification title based on conversation type
        if conv_type == 'GROUP':
            group_name = conversation.get('groupName', 'Group')
            notification_title = f"{sender_name} in {group_name}"
        else:
            notification_title = sender_name
        
        # Truncate message text to 100 characters
        notification_body = message_text[:100]
        if len(message_text) > 100:
            notification_body += "..."
        
        # Create multicast message
        multicast_message = messaging.MulticastMessage(
            notification=messaging.Notification(
                title=notification_title,
                body=notification_body
            ),
            data={
                'chatId': conversation_id,
                'messageId': message_id,
                'senderId': sender_id,
                'type': 'new_message'
            },
            tokens=tokens,
            android=messaging.AndroidConfig(
                priority='high',
                notification=messaging.AndroidNotification(
                    channel_id='messages',
                    sound='default',
                    priority='high'
                )
            )
        )
        
        # Send notifications
        response = messaging.send_multicast(multicast_message)
        
        logger.info(f"Sent {response.success_count} notifications, {response.failure_count} failures")
        
        # Clean up invalid tokens
        if response.failure_count > 0:
            invalid_tokens = []
            for idx, resp in enumerate(response.responses):
                if not resp.success:
                    error_code = resp.exception.code if resp.exception else None
                    if error_code in ['registration-token-not-registered', 'invalid-registration-token']:
                        invalid_tokens.append(tokens[idx])
            
            # Delete invalid tokens from Firestore
            for user_id in recipient_ids:
                for invalid_token in invalid_tokens:
                    try:
                        db.collection('users').document(user_id).collection('fcmTokens').document(invalid_token).delete()
                        logger.info(f"Deleted invalid token: {invalid_token}")
                    except Exception as e:
                        logger.error(f"Failed to delete token {invalid_token}: {e}")
        
    except Exception as e:
        logger.error(f"Error sending notification: {e}", exc_info=True)
        # Don't raise - we don't want to retry indefinitely

