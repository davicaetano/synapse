# Initial Setup - Step 1: Android Project Bootstrap

Scope: Create an Android app (Kotlin + Jetpack Compose) prepared for Firebase and FCM hybrid push.

## Targets
- Min SDK: 26
- Compile/Target SDK: latest stable (to be set by Android Studio)
- Language: Kotlin
- UI: Jetpack Compose

## Dependencies (to add)
- Firebase BOM (Firestore, Realtime DB, Auth, Messaging, Storage)
- Play Services base
- AndroidX (Core KTX, Activity Compose, Lifecycle, Navigation)
- Coil (image loading)

## Messaging (Hybrid) Requirements
- Create NotificationChannel ("messages") on app start (API 26+)
- Request POST_NOTIFICATIONS (Android 13+)
- Implement FirebaseMessagingService to handle foreground messages (custom notif)
- Use deep link to open Chat with extras (chatId, messageId)
- Deduplicate via collapse_key per chat

## Presence & Typing (Realtime DB)
- presence: /presence/{userId} { online: true/false, lastSeen, activeChatId }
- typing: /typing/{chatId}/{userId}: true/false (expire client-side after 3s)

## Firestore Collections (baseline)
- users/{userId}
- chats/{chatId}/messages/{messageId}
- groups/{groupId}

## Token Management
- Store tokens in users/{userId}/fcmTokens/{token} with metadata
- onNewToken -> upsert token; remove invalid tokens on NotRegistered

## Next Steps
1. Generate Android Studio project under /android
2. Add Firebase SDK via Gradle + google-services plugin
3. Implement MessagingService + NotificationChannel
4. Wire basic navigation and Chat placeholder
5. Prepare Cloud Function trigger for hybrid push
