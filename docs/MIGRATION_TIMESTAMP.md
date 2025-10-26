# 🔄 Migration Guide: Milliseconds → Timestamp

**Migrating from `createdAtMs: Long` to `localTimestamp: Timestamp`**

---

## 📊 Summary

**Before:** Mixed formats (milliseconds for some fields, Timestamp for others)  
**After:** Consistent Timestamp format for ALL time fields

---

## 🎯 Changes Overview

| Category | Files to Change | Lines Affected |
|----------|----------------|----------------|
| **Entities** | 3 files | ~15 lines |
| **DataSources** | 2 files | ~40 lines |
| **Schema Doc** | 1 file (new) | Documentation |
| **Total** | **6 files** | **~55 lines** |

---

## 📝 File Changes

### 1. **Entity Classes** (3 files)

#### `MessageEntity.kt`
```kotlin
// BEFORE
data class MessageEntity(
    val createdAtMs: Long,
    val serverTimestamp: Long?,
    val deletedAtMs: Long?
)

// AFTER
data class MessageEntity(
    val localTimestamp: Timestamp,
    val serverTimestamp: Timestamp?,
    val deletedAt: Timestamp?
)
```

#### `ConversationEntity.kt`
```kotlin
// BEFORE
data class ConversationEntity(
    val createdAtMs: Long,
    val updatedAtMs: Long
)

// AFTER
data class ConversationEntity(
    val localTimestamp: Timestamp,
    val updatedAt: Timestamp
)
```

#### `FCMTokenEntity.kt`
```kotlin
// BEFORE (if exists)
val createdAtMs: Long

// AFTER
val localTimestamp: Timestamp
```

---

### 2. **FirestoreMessageDataSource.kt** (~30 changes)

#### Queries:
```kotlin
// BEFORE
.orderBy("createdAtMs", Query.Direction.DESCENDING)
.whereGreaterThan("createdAtMs", timestamp)
.whereLessThan("createdAtMs", timestamp)

// AFTER
.orderBy("localTimestamp", Query.Direction.DESCENDING)
.whereGreaterThan("localTimestamp", timestamp)
.whereLessThan("localTimestamp", timestamp)
```

#### Read (Parse):
```kotlin
// BEFORE
createdAtMs = doc.getLong("createdAtMs") ?: 0L
serverTimestamp = doc.getTimestamp("serverTimestamp")?.toDate()?.time
deletedAtMs = doc.getLong("deletedAtMs")

// AFTER
localTimestamp = doc.getTimestamp("localTimestamp") ?: Timestamp.now()
serverTimestamp = doc.getTimestamp("serverTimestamp")
deletedAt = doc.getTimestamp("deletedAt")
```

#### Write:
```kotlin
// BEFORE
"createdAtMs" to System.currentTimeMillis()
"deletedAtMs" to timestamp

// AFTER
"localTimestamp" to Timestamp.now()
"deletedAt" to Timestamp.now()
```

#### Method Signatures:
```kotlin
// BEFORE
fun listenMessages(conversationId: String, afterTimestamp: Long? = null)
suspend fun fetchOlderMessages(conversationId: String, beforeTimestamp: Long, limit: Int = 200)
suspend fun getUnreadCount(conversationId: String, userId: String, lastSeenAtMs: Long)
suspend fun sendMessageAs(..., createdAtMs: Long = System.currentTimeMillis())
suspend fun deleteMessage(..., timestamp: Long)

// AFTER
fun listenMessages(conversationId: String, afterTimestamp: Timestamp? = null)
suspend fun fetchOlderMessages(conversationId: String, beforeTimestamp: Timestamp, limit: Int = 200)
suspend fun getUnreadCount(conversationId: String, userId: String, lastSeenAt: Timestamp)
suspend fun sendMessageAs(..., localTimestamp: Timestamp = Timestamp.now())
suspend fun deleteMessage(..., deletedAt: Timestamp)
```

---

### 3. **FirestoreConversationDataSource.kt** (~10 changes)

#### Read (Parse):
```kotlin
// BEFORE
updatedAtMs = doc.getLong("updatedAtMs") ?: 0L
createdAtMs = doc.getLong("createdAtMs") ?: 0L

// AFTER
updatedAt = doc.getTimestamp("updatedAt") ?: Timestamp.now()
localTimestamp = doc.getTimestamp("localTimestamp") ?: Timestamp.now()
```

#### Write:
```kotlin
// BEFORE
"createdAtMs" to System.currentTimeMillis()
"updatedAtMs" to System.currentTimeMillis()

// AFTER
"localTimestamp" to Timestamp.now()
"updatedAt" to Timestamp.now()
```

---

## 🚀 Implementation Steps

### Step 1: Update Entity Classes
- [ ] MessageEntity.kt
- [ ] ConversationEntity.kt
- [ ] FCMTokenEntity.kt (if exists)

### Step 2: Update FirestoreMessageDataSource.kt
- [ ] Change all queries (`orderBy`, `where`)
- [ ] Change all parse operations (`getLong` → `getTimestamp`)
- [ ] Change all write operations (`System.currentTimeMillis()` → `Timestamp.now()`)
- [ ] Update method signatures
- [ ] Fix batch offset logic

### Step 3: Update FirestoreConversationDataSource.kt
- [ ] Change all parse operations
- [ ] Change all write operations

### Step 4: Verify Compilation
- [ ] Build project
- [ ] Fix any remaining type mismatches
- [ ] Run app and test

### Step 5: Clean Old Data
- [ ] Delete all test conversations in Firebase Console
- [ ] Re-run test scripts with new schema
- [ ] Verify in Firebase Console (timestamps should be readable)

---

## ⚠️ Breaking Changes

**Database Migration:** This is a BREAKING CHANGE. Old messages with `createdAtMs` will NOT work with new code.

**Solution:** Delete all test data and start fresh.

**Production:** If this were production, you'd need a migration script to convert all existing documents.

---

## 🧪 Testing Checklist

After migration:

- [ ] Send single message (check `localTimestamp` in Console)
- [ ] Send batch messages (check +1ms offset)
- [ ] Send message offline (check appears immediately)
- [ ] Sync offline message (check `serverTimestamp` populated)
- [ ] Order messages (newest first)
- [ ] Lazy load older messages
- [ ] Count unread messages
- [ ] Delete message (check `deletedAt`)
- [ ] Run AI features (check bot's `lastMessageSentAt`)

---

## 📚 References

- **Schema Spec:** `docs/FIREBASE_SCHEMA.md`
- **Original Issue:** Mixed timestamp formats causing confusion
- **Solution:** Standardize on Firestore Timestamp for all fields

---

**Next Steps:** Implement changes in order listed above. Test thoroughly before committing.

