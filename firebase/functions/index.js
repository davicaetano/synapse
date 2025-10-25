const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Send push notification when a new message is created
 * Trigger: Firestore onCreate at conversations/{convId}/messages/{msgId}
 */
exports.sendNotificationOnNewMessage = functions.firestore
  .document('conversations/{conversationId}/messages/{messageId}')
  .onCreate(async (snap, context) => {
    try {
      const message = snap.data();
      const conversationId = context.params.conversationId;
      const messageId = context.params.messageId;
      
      console.log(`New message ${messageId} in conversation ${conversationId}`);
      
      // Check if notification should be sent (default: true)
      // Welcome messages have sendNotification = false
      const shouldSendNotification = message.sendNotification !== false;
      
      if (!shouldSendNotification) {
        console.log('Skipping notification (sendNotification = false)');
        return null;
      }
      
      // Get conversation document
      const convDoc = await admin.firestore()
        .collection('conversations')
        .doc(conversationId)
        .get();
      
      if (!convDoc.exists) {
        console.log('Conversation not found');
        return null;
      }
      
      const conversation = convDoc.data();
      const memberIds = conversation.memberIds || [];
      const senderId = message.senderId;
      const messageText = message.text || '';
      
      // Get sender's name
      const senderDoc = await admin.firestore()
        .collection('users')
        .doc(senderId)
        .get();
      
      const senderName = senderDoc.exists 
        ? (senderDoc.data().displayName || 'Someone')
        : 'Someone';
      
      // Get recipients (exclude sender)
      const recipientIds = memberIds.filter(id => id !== senderId);
      
      if (recipientIds.length === 0) {
        console.log('No recipients to notify');
        return null;
      }
      
      // Get FCM tokens
      const tokens = [];
      for (const userId of recipientIds) {
        const tokensSnapshot = await admin.firestore()
          .collection('users')
          .doc(userId)
          .collection('fcmTokens')
          .get();
        
        tokensSnapshot.docs.forEach(doc => {
          tokens.push(doc.id);
        });
      }
      
      if (tokens.length === 0) {
        console.log('No FCM tokens found');
        return null;
      }
      
      // Build title
      let title = senderName;
      if (conversation.convType === 'GROUP') {
        const groupName = conversation.groupName || 'Group';
        title = `${senderName} in ${groupName}`;
      }
      
      // Send notifications
      const payload = {
        notification: {
          title: title,
          body: messageText.substring(0, 100),
        },
        data: {
          chatId: conversationId,
          messageId: messageId,
          senderId: senderId,
          type: 'new_message'
        }
      };
      
      const response = await admin.messaging().sendEachForMulticast({
        tokens: tokens,
        ...payload,
        android: {
          priority: 'high',
          notification: {
            channelId: 'messages',
            sound: 'default'
          }
        }
      });
      
      console.log(`Sent ${response.successCount} notifications, ${response.failureCount} failures`);
      
      // Clean up invalid tokens
      if (response.failureCount > 0) {
        const invalidTokens = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success && resp.error) {
            const errorCode = resp.error.code;
            if (errorCode === 'messaging/invalid-registration-token' ||
                errorCode === 'messaging/registration-token-not-registered') {
              invalidTokens.push(tokens[idx]);
            }
          }
        });
        
        for (const userId of recipientIds) {
          for (const invalidToken of invalidTokens) {
            try {
              await admin.firestore()
                .collection('users')
                .doc(userId)
                .collection('fcmTokens')
                .doc(invalidToken)
                .delete();
              console.log(`Deleted invalid token: ${invalidToken}`);
            } catch (err) {
              console.error(`Failed to delete token: ${err}`);
            }
          }
        }
      }
      
      return null;
    } catch (error) {
      console.error('Error sending notification:', error);
      return null;
    }
  });

