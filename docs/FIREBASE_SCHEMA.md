# 🔥 Firebase Firestore Schema - Synapse

**Official data structure specification for all platforms (Android, Python, Node.js)**

> ⚠️ **IMPORTANT:** All platforms MUST follow this schema to ensure consistency.

---

## 📚 Collections Structure

```
Firestore
│
├── 📁 conversations/                    (Collection)
│   ├── 📄 {conversationId}/            (Document)
│   │   │
│   │   ├── 📁 messages/                (Subcollection)
│   │   │   ├── 📄 {messageId}/         (Document - see Message Schema)
│   │   │   └── ...
│   │   │
│   │   └── [See Conversation Schema]
│   │
│   └── ...
│
└── 📁 users/                            (Collection)
    ├── 📄 {userId}/                     (Document - see User Schema)
    ├── 📄 synapse-bot-system/           (Special: AI bot)
    └── ...
```

---

## 💬 Message Schema

**Path:** `conversations/{conversationId}/messages/{messageId}`

### TypeScript Definition:

```typescript
interface Message {
  // ============================================================
  // TIMING (Timestamp format)
  // ============================================================
  localTimestamp: Timestamp;      // Client-created (ordering, queries)
  serverTimestamp: Timestamp;     // Server-created (authority, comparisons)
  
  // ============================================================
  // CORE FIELDS
  // ============================================================
  text: string;                   // Message content
  senderId: string;               // User ID who sent (or "synapse-bot-system")
  memberIdsAtCreation: string[];  // Snapshot of group members when message was created
  
  // ============================================================
  // METADATA
  // ============================================================
  type: MessageType;              // Message type (see enum below)
  isDeleted: boolean;             // Soft delete flag
  sendNotification: boolean;      // Whether to send push notification
  
  // ============================================================
  // SOFT DELETE (Optional)
  // ============================================================
  deletedAt?: Timestamp;          // When was deleted
  deletedBy?: string;             // User ID who deleted
  
  // ============================================================
  // AI METADATA (Optional - only for AI messages)
  // ============================================================
  metadata?: {
    generatedBy?: string;         // User ID who triggered AI
    messageCount?: number;        // Number of messages analyzed
    priorityCount?: number;       // For ai_priority type
    decisionsCount?: number;      // For ai_decisions type
    customInstructions?: string;  // For ai_summary type
    aiGenerated?: boolean;        // Flag for AI-generated content
  };
}
```

### Message Types:

```typescript
type MessageType = 
  | "text"              // Normal user text message
  | "bot"               // System/bot message (welcome, etc)
  | "ai_summary"        // AI thread summarization
  | "ai_action_items"   // AI action items extraction
  | "ai_priority"       // AI priority detection
  | "ai_decisions"      // AI decision tracking
  | "ai_error";         // AI error message
```

---

## 💬 Example Message Documents:

### User Text Message:
```javascript
{
  localTimestamp: Timestamp("2025-10-26 10:00:00.123"),
  serverTimestamp: Timestamp("2025-10-26 10:00:00.500"),
  text: "Hey, how are you?",
  senderId: "user123",
  memberIdsAtCreation: ["user123", "user456"],
  type: "text",
  isDeleted: false,
  sendNotification: true
}
```

### AI Summary Message:
```javascript
{
  localTimestamp: Timestamp("2025-10-26 10:05:00.000"),
  serverTimestamp: Timestamp("2025-10-26 10:05:00.200"),
  text: "📋 **Thread Summary**\n\nKey points discussed:\n- Project timeline\n- Budget approval\n...",
  senderId: "synapse-bot-system",
  memberIdsAtCreation: ["user123", "user456"],
  type: "ai_summary",
  isDeleted: false,
  sendNotification: false,
  metadata: {
    generatedBy: "user123",
    messageCount: 50,
    customInstructions: "Focus on action items",
    aiGenerated: true
  }
}
```

### Batch Messages (with offset):
```javascript
// Message 1
{
  localTimestamp: Timestamp("2025-10-26 10:00:00.100"),  // +0ms
  serverTimestamp: Timestamp("2025-10-26 10:00:00.500"),
  text: "First message",
  // ...
}

// Message 2
{
  localTimestamp: Timestamp("2025-10-26 10:00:00.101"),  // +1ms
  serverTimestamp: Timestamp("2025-10-26 10:00:00.500"),  // Same server time
  text: "Second message",
  // ...
}

// Message 3
{
  localTimestamp: Timestamp("2025-10-26 10:00:00.102"),  // +2ms
  serverTimestamp: Timestamp("2025-10-26 10:00:00.500"),  // Same server time
  text: "Third message",
  // ...
}
```

---

## 🗣️ Conversation Schema

**Path:** `conversations/{conversationId}`

### TypeScript Definition:

```typescript
interface Conversation {
  // ============================================================
  // CORE FIELDS
  // ============================================================
  id: string;                     // Auto-generated document ID
  convType: ConversationType;     // Conversation type (see enum below)
  
  // ============================================================
  // TIMING (Timestamp format)
  // ============================================================
  localTimestamp: Timestamp;      // When conversation was created
  updatedAt: Timestamp;           // Last activity timestamp
  
  // ============================================================
  // GROUP FIELDS (Optional - only for GROUP type)
  // ============================================================
  groupName?: string;             // Group display name
  createdBy?: string;             // User ID who created the group
  
  // ============================================================
  // PREVIEW
  // ============================================================
  lastMessageText: string;        // Preview of last message (max 100 chars)
  
  // ============================================================
  // MEMBERS (Map<userId, Member>)
  // NOTE: This is the source of truth for membership
  // ============================================================
  members: {
    [userId: string]: {
      lastSeenAt: Timestamp;          // When member last viewed conversation
      lastReceivedAt: Timestamp;      // When member last received messages
      lastMessageSentAt: Timestamp;   // When member last sent a message
      isBot: boolean;                 // True for synapse-bot-system
      isAdmin: boolean;               // True for group creator/admins
      isDeleted: boolean;             // True when member leaves group (soft delete)
    }
  };
  
  // ============================================================
  // QUERY OPTIMIZATION (Auto-synced by Cloud Function)
  // ⚠️ DO NOT WRITE THIS FIELD MANUALLY - READ ONLY FOR CLIENTS
  // ============================================================
  memberIds: string[];            // Array of active member IDs (no bots, no deleted)
                                  // Auto-synced by Cloud Function from members map
                                  // Used ONLY for Firestore query: .whereArrayContains("memberIds", userId)
}
```

### Conversation Types:

```typescript
type ConversationType = 
  | "DIRECT"    // 1-on-1 conversation (2 members)
  | "GROUP"     // Group conversation (2+ members)
  | "SELF";     // Self conversation (1 member - notes/AI assistant)
```

---

## 🗣️ Example Conversation Documents:

### Direct Conversation:
```javascript
{
  id: "conv123",
  convType: "DIRECT",
  localTimestamp: Timestamp("2025-10-26 09:00:00.000"),
  updatedAt: Timestamp("2025-10-26 10:00:00.000"),
  lastMessageText: "Hey, how are you?",
  memberIds: ["user123", "user456"],  // Auto-synced by Cloud Function (no bots, no deleted)
  members: {
    "user123": {
      lastSeenAt: Timestamp("2025-10-26 10:00:00.000"),
      lastReceivedAt: Timestamp("2025-10-26 09:30:00.000"),
      lastMessageSentAt: Timestamp("2025-10-26 10:00:00.000"),
      isBot: false,
      isAdmin: false,
      isDeleted: false
    },
    "user456": {
      lastSeenAt: Timestamp("2025-10-26 09:45:00.000"),
      lastReceivedAt: Timestamp("2025-10-26 10:00:00.000"),
      lastMessageSentAt: Timestamp("2025-10-26 09:30:00.000"),
      isBot: false,
      isAdmin: false,
      isDeleted: false
    },
    "synapse-bot-system": {
      lastSeenAt: Timestamp("2025-10-26 10:05:00.000"),
      lastReceivedAt: Timestamp("2025-10-26 10:05:00.000"),
      lastMessageSentAt: Timestamp("2025-10-26 10:05:00.000"),
      isBot: true,
      isAdmin: false,
      isDeleted: false
    }
  }
}
```

### Group Conversation:
```javascript
{
  id: "conv456",
  convType: "GROUP",
  groupName: "SpaceX Starship",
  createdBy: "user123",
  localTimestamp: Timestamp("2025-10-26 08:00:00.000"),
  updatedAt: Timestamp("2025-10-26 10:30:00.000"),
  lastMessageText: "Meeting tomorrow at 9am sharp...",
  memberIds: ["user123", "user789"],  // Auto-synced by Cloud Function (user456 left group)
  members: {
    "user123": {
      lastSeenAt: Timestamp("2025-10-26 10:30:00.000"),
      lastReceivedAt: Timestamp("2025-10-26 10:30:00.000"),
      lastMessageSentAt: Timestamp("2025-10-26 10:30:00.000"),
      isBot: false,
      isAdmin: true,  // Creator is admin
      isDeleted: false
    },
    "user456": {
      lastSeenAt: Timestamp("2025-10-26 09:00:00.000"),
      lastReceivedAt: Timestamp("2025-10-26 10:30:00.000"),
      lastMessageSentAt: Timestamp("2025-10-26 09:15:00.000"),
      isBot: false,
      isAdmin: false,
      isDeleted: true  // Left the group (soft delete)
    },
    "user789": {
      lastSeenAt: Timestamp("2025-10-26 10:25:00.000"),
      lastReceivedAt: Timestamp("2025-10-26 10:30:00.000"),
      lastMessageSentAt: Timestamp("2025-10-26 10:00:00.000"),
      isBot: false,
      isAdmin: false,
      isDeleted: false
    },
    "synapse-bot-system": {
      lastSeenAt: Timestamp("2025-10-26 10:30:00.000"),
      lastReceivedAt: Timestamp("2025-10-26 10:30:00.000"),
      lastMessageSentAt: Timestamp("2025-10-26 10:30:00.000"),
      isBot: true,
      isAdmin: false,
      isDeleted: false  // Bot never leaves
    }
  }
}
```

---

## 👤 User Schema

**Path:** `users/{userId}`

### TypeScript Definition:

```typescript
interface User {
  id: string;                     // User ID (same as document ID)
  email: string;                  // User email
  displayName: string;            // Display name
  photoUrl?: string;              // Profile photo URL
  createdAt: Timestamp;           // Account creation time
  updatedAt: Timestamp;           // Last profile update
  isSystemBot?: boolean;          // True for synapse-bot-system
}
```

---

## 🔑 Key Design Decisions

### 1. Timestamp Format (MANDATORY)

**Rule:** ALL timestamp fields MUST use `Firestore Timestamp` format, NOT milliseconds.

**Correct:**
```javascript
localTimestamp: Timestamp("2025-10-26 10:00:00.123")
serverTimestamp: FieldValue.serverTimestamp()
```

**Incorrect:**
```javascript
createdAtMs: 1730000000000  // ❌ NEVER use milliseconds
```

**Why?**
- ✅ Human-readable in Firebase Console
- ✅ Consistent across all platforms
- ✅ Includes nanoseconds for precision
- ✅ No conversion needed

---

### 2. Ordering Strategy

**Messages MUST be ordered by `localTimestamp` (primary):**

```kotlin
// Android (Kotlin)
.orderBy("localTimestamp", Query.Direction.DESCENDING)

// Python
.order_by('localTimestamp', direction=firestore.Query.DESCENDING)

// Node.js
.orderBy('localTimestamp', 'desc')
```

**Why `localTimestamp` first?**
- ✅ Works offline (serverTimestamp is pending)
- ✅ Batch messages have unique values (+1ms offset)
- ✅ `serverTimestamp` used for comparisons, not ordering

---

### 3. Bot in members (identified by isBot flag)

**Rule:** The bot (`synapse-bot-system`) MUST be in `members` with `isBot: true`.

**Why?**
- ✅ Bot cannot be removed from group (`isDeleted` always false for bot)
- ✅ Android needs `lastMessageSentAt` for unread counting
- ✅ Cloud Function updates bot's status automatically
- ✅ `isBot` flag makes it easy to filter/identify bot

---

### 4. Auto-Synced memberIds Array

**Rule:** `memberIds` is pre-populated on creation and kept in sync by Cloud Function.

**Purpose:**
- Used ONLY for inbox query: `.whereArrayContains("memberIds", userId)`
- Firestore cannot efficiently query map keys, so we need an array

**How it works:**
1. **On creation:** Client pre-populates `memberIds` with all member IDs (for instant inbox visibility)
2. **After creation:** Cloud Function (`syncMemberIds`) keeps it in sync when members change
3. **Filtering:** Cloud Function removes bots and deleted members from the array

**Example - Creation:**
```kotlin
// Android creates conversation with initial memberIds:
val data = mapOf(
  "memberIds" to listOf("user123", "user456"),  // Pre-populated for instant visibility
  "members" to mapOf(
    "user123" to Member(...),
    "user456" to Member(...)
  )
)
```

**Example - After member leaves:**
```javascript
// User456 leaves group (sets isDeleted: true)
members: {
  "user123": { isBot: false, isDeleted: false, ... },
  "user456": { isBot: false, isDeleted: true, ... },   // Left group
  "synapse-bot-system": { isBot: true, isDeleted: false, ... }
}

// Cloud Function auto-syncs:
memberIds: ["user123"]  // Removed user456 (deleted) and bot
```

**⚠️ IMPORTANT:**
- ✅ Client writes `memberIds` ONLY on conversation creation
- ❌ Client NEVER updates `memberIds` after creation
- ❌ NEVER rely on `memberIds` for member data (use `members` map)
- ✅ ONLY use `memberIds` for Firestore queries

---

### 5. Batch Message Offset

**Rule:** When sending multiple messages in batch, add +1ms offset to `localTimestamp`.

```kotlin
// Android example
val baseTime = System.currentTimeMillis()
messages.forEachIndexed { index, text ->
    val localTimestamp = Timestamp(Date(baseTime + index))  // +0ms, +1ms, +2ms...
}
```

```javascript
// Node.js example
const baseTime = Date.now();
messages.forEach((msg, index) => {
    const localTimestamp = admin.firestore.Timestamp.fromMillis(baseTime + index);
});
```

**Why?**
- ✅ Guarantees unique ordering
- ✅ Even if `serverTimestamp` is the same

---

### 5. Cloud Function Auto-Updates

**Rule:** Cloud Functions automatically update `memberStatus` and `lastMessageText` on new messages.

**Clients MUST:**
- ✅ Create message with required fields
- ❌ NOT manually update `memberStatus[senderId].lastMessageSentAt`
- ❌ NOT manually update `conversation.lastMessageText`

**Cloud Function handles:**
- ✅ `memberStatus[senderId].lastMessageSentAt = serverTimestamp()`
- ✅ `conversation.lastMessageText = message.text`
- ✅ `conversation.updatedAt = message.localTimestamp`

---

## 🔧 Platform-Specific Examples

### Android (Kotlin):

```kotlin
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.util.Date

// Create message
val messageData = hashMapOf(
    "localTimestamp" to Timestamp.now(),
    "serverTimestamp" to FieldValue.serverTimestamp(),
    "text" to "Hello!",
    "senderId" to userId,
    "memberIdsAtCreation" to memberIds,
    "type" to "text",
    "isDeleted" to false,
    "sendNotification" to true
)

// Query messages
firestore.collection("conversations")
    .document(conversationId)
    .collection("messages")
    .orderBy("localTimestamp", Query.Direction.DESCENDING)
    .get()
```

---

### Python (Backend):

```python
from google.cloud.firestore import SERVER_TIMESTAMP
from google.cloud import firestore
from datetime import datetime

# Create message
message_data = {
    'localTimestamp': firestore.Timestamp.from_date(datetime.now()),
    'serverTimestamp': SERVER_TIMESTAMP,
    'text': 'AI Summary generated',
    'senderId': 'synapse-bot-system',
    'memberIdsAtCreation': member_ids,
    'type': 'ai_summary',
    'isDeleted': False,
    'sendNotification': False,
    'metadata': {
        'generatedBy': user_id,
        'messageCount': 50,
        'aiGenerated': True
    }
}

# Query messages
messages_ref.order_by('localTimestamp', direction=firestore.Query.DESCENDING).stream()
```

---

### Node.js (Scripts/Cloud Functions):

```javascript
const admin = require('firebase-admin');

// Create message
const messageData = {
  localTimestamp: admin.firestore.Timestamp.now(),
  serverTimestamp: admin.firestore.FieldValue.serverTimestamp(),
  text: 'Hello from script!',
  senderId: userId,
  memberIdsAtCreation: memberIds,
  type: 'text',
  isDeleted: false,
  sendNotification: false
};

// Query messages
await db.collection('conversations')
  .doc(conversationId)
  .collection('messages')
  .orderBy('localTimestamp', 'desc')
  .get();
```

---

## 🚨 Common Mistakes to Avoid

### ❌ Using milliseconds instead of Timestamp:
```javascript
// WRONG
createdAtMs: 1730000000000

// CORRECT
localTimestamp: Timestamp("2025-10-26 10:00:00.000")
```

---

### ❌ Ordering by serverTimestamp first:
```kotlin
// WRONG (won't work offline)
.orderBy("serverTimestamp", Query.Direction.DESCENDING)

// CORRECT
.orderBy("localTimestamp", Query.Direction.DESCENDING)
```

---

### ❌ Forgetting isBot/isAdmin/isDeleted flags:
```javascript
// WRONG (missing flags)
{
  members: {
    "user1": {
      lastSeenAt: Timestamp,
      lastReceivedAt: Timestamp,
      lastMessageSentAt: Timestamp
      // ❌ Missing: isBot, isAdmin, isDeleted
    }
  }
}

// CORRECT (all flags present)
{
  members: {
    "user1": {
      lastSeenAt: Timestamp,
      lastReceivedAt: Timestamp,
      lastMessageSentAt: Timestamp,
      isBot: false,  // ✅
      isAdmin: true,  // ✅
      isDeleted: false  // ✅
    }
  }
}
```

---

### ❌ Forgetting offset in batch:
```kotlin
// WRONG (all messages have same timestamp)
messages.forEach { text ->
    val localTimestamp = Timestamp.now()  // Same for all!
}

// CORRECT (each message +1ms)
val baseTime = System.currentTimeMillis()
messages.forEachIndexed { index, text ->
    val localTimestamp = Timestamp(Date(baseTime + index))  // Unique!
}
```

---

## 📊 Firestore Indexes

**Required composite indexes:**

```json
{
  "indexes": [
    {
      "collectionGroup": "messages",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "localTimestamp", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "messages",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "isDeleted", "order": "ASCENDING" },
        { "fieldPath": "localTimestamp", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "messages",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "senderId", "order": "ASCENDING" },
        { "fieldPath": "localTimestamp", "order": "DESCENDING" }
      ]
    }
  ]
}
```

---

## 📝 Version History

- **v2.0** (2025-10-26): Migrated from milliseconds to Timestamp format
- **v1.0** (2025-10-20): Initial schema definition

---

## ✅ Checklist for New Features

Before implementing any feature that writes to Firestore:

- [ ] Use `localTimestamp: Timestamp` (not milliseconds)
- [ ] Use `serverTimestamp: FieldValue.serverTimestamp()`
- [ ] Include `memberIdsAtCreation` for messages
- [ ] Set `sendNotification` flag appropriately
- [ ] For batch: add +1ms offset to `localTimestamp`
- [ ] Order by `localTimestamp` (not `serverTimestamp`)
- [ ] Bot goes in `memberStatus`, NOT `memberIds`
- [ ] Test offline functionality
- [ ] Verify in Firebase Console (timestamps should be readable)

---

**🎯 Remember:** Consistency across platforms is critical. When in doubt, refer to this document!

