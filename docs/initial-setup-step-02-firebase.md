# Initial Setup - Step 02: Firebase

Purpose: Plan Firebase setup for real-time messaging, presence, media, and push notifications, aligned with the hybrid notification strategy.

## Scope
- Create Firebase projects: synapse-dev and synapse-prod
- Enable: Firestore, Realtime Database, Authentication, Cloud Messaging (FCM), Storage
- Add Android app (package com.synapse), download google-services.json per env
- Establish baseline security rules (Dev permissive, Prod stricter)
- Plan Cloud Functions for hybrid push (notification + data)
- Define presence/typing locations in Realtime DB
- Define token storage strategy

## Services & Configuration
- Firestore: messages/chats/users/groups (persistent data)
- Realtime Database: presence/typing (ephemeral state)
- Auth: Email/Password (MVP)
- Cloud Messaging: Push notifications
- Storage: Image uploads (later), thumbnails optional

## Android App Registration
- Package: com.synapse
- App name: Synapse
- Min SDK: 26, Target SDK: 36
- Download google-services.json and place under:
  - android/app/google-services.json for the active environment
- We will add the Gradle google-services plugin only after the file exists to avoid build errors.

## Environment Strategy
- Two Firebase projects: synapse-dev and synapse-prod
- Dev: easier rules for iteration; seeded test data
- Prod: stricter rules; clean data
- Store environment selection in app settings (DataStore) via Dev Menu (debug only)

## Data Model (Baseline)
- Firestore:
  - users/{userId}: profile, displayName, photoUrl
  - chats/{chatId}/messages/{messageId}: senderId, text, createdAt, delivery/read meta
  - groups/{groupId}: members[], name
- Read receipts: per-message map readBy.{userId} = timestamp
- Delivery states: computed client-side + updated by recipients (no server write required initially)

## Realtime Database (Presence & Typing)
- Presence: /presence/{userId}
  - online: boolean
  - lastSeen: timestamp
  - activeChatId: string | null
- Typing: /typing/{chatId}/{userId}: boolean (client expires after 3s idle)

## FCM Token Management
- users/{userId}/fcmTokens/{token} with metadata:
  - createdAt, platform: "android", brand/model, sdk
- On invalid token (NotRegistered), delete the doc

## Hybrid Push (Cloud Functions)
- Trigger: Firestore onCreate chats/{chatId}/messages/{messageId}
- Steps:
  1) Read recipients for the chat
  2) Check presence: if activeChatId == chatId, suppress push for that user
  3) For others, send a single FCM with both notification and data
- Payload example:
```json
{
  "android": { "priority": "high" },
  "notification": { "title": "<senderName>", "body": "<preview>" },
  "data": {
    "type": "message",
    "chatId": "<chatId>",
    "messageId": "<messageId>",
    "senderId": "<senderId>",
    "preview": "<truncatedText>"
  }
}
```
- Android behavior:
  - Foreground: onMessageReceived -> custom UI/notification
  - Background/killed/force-stopped: system shows notification; tap opens ChatActivity

## Security Rules (Baseline)
- Firestore (Dev): allow reads/writes for authenticated users; later restrict to members of chat
- Firestore (Prod): users can read/write only chats they belong to; server validates membership
- Realtime DB: users can write their own presence/typing only
- Storage: users write to their own paths; read controlled by chat membership

## Files to Add (later steps)
- firebase/firestore.rules
- firebase/firestore.indexes.json
- firebase/database.rules.json
- firebase/storage.rules
- firebase/firebase.json
- backend/functions/ (if using Cloud Functions) or use FastAPI to send FCM

## Secrets & Git Hygiene
- Never commit:
  - android/app/google-services.json
  - Service account JSONs
  - .env files
- Keep .gitignore as already configured; only commit .env.example

## Next Steps
1. Create Firebase project synapse-dev and register Android app com.synapse
2. Download google-services.json -> place at android/app/
3. Add Gradle google-services plugin and Firebase BoM for Firestore/Auth/Realtime/Storage
4. Initialize SDK (no writes yet) and verify app launches
5. Create presence write on app start + onDisconnect handler (later)
6. Add Cloud Function scaffold for hybrid push (later after Firestore writes exist)
