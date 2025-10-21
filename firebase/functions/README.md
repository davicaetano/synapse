# Firebase Cloud Functions - Push Notifications (Python)

## Setup

### 1. Install Firebase CLI
```bash
npm install -g firebase-tools
```

### 2. Login to Firebase
```bash
firebase login
```

### 3. Initialize Functions (if not done)
```bash
cd firebase
firebase init functions
# Select existing project: synapse-dev (or your project)
# Language: Python
# Install dependencies: Yes
```

### 4. Install Dependencies (if needed)
```bash
cd functions
pip install -r requirements.txt
```

### 5. Deploy Functions
```bash
# From firebase/ directory
firebase deploy --only functions

# Or deploy specific function
firebase deploy --only functions:send_notification_on_new_message
```

## Functions

### `send_notification_on_new_message` (Python)
**Trigger**: Firestore onCreate at `conversations/{convId}/messages/{msgId}`

**What it does**:
1. Gets conversation document to find members
2. Gets sender's name from users collection
3. Gets FCM tokens of all recipients (excludes sender)
4. Sends push notification to all recipients via multicast
5. Automatically cleans up invalid/expired tokens

**Notification payload**:
- **Title**: "SenderName" (or "SenderName in GroupName" for groups)
- **Body**: Message text (first 100 chars)
- **Data**: chatId, messageId, senderId (for deep linking)
- **Android Config**: High priority, messages channel, sound enabled

## Testing

### Test locally (emulator)
```bash
cd functions
npm install -g firebase-functions-emulator
firebase emulators:start --only functions,firestore
```

### Test in production
1. Send a message in the app
2. Check Firebase Console → Functions → Logs
3. Recipient should receive push notification

## Troubleshooting

### Notifications not sending?
1. Check Firebase Console → Functions → Logs for errors
2. Verify FCM tokens exist in Firestore: `users/{id}/fcmTokens/`
3. Check notification permission is granted on device
4. Verify Firebase Cloud Messaging API is enabled in Google Cloud Console

### Invalid tokens?
Function automatically removes invalid tokens from Firestore.

## Cost

- Free tier: 2M invocations/month
- This function runs once per message sent
- Expected usage for MVP: < 1000/month (well within free tier)

