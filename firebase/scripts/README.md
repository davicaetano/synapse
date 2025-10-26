# Firebase Admin Scripts

## Setup

1. Download your Firebase service account key:
   - Go to Firebase Console → Project Settings → Service Accounts
   - Click "Generate new private key"
   - Save as `service-account-key.json` in this folder

2. Install dependencies:
```bash
npm install
```

## Create Test Users

Create 10 test users with default password:

```bash
node create-test-users.js 10
```

This will create:
- Alice Johnson (alice@test.com)
- Bob Smith (bob@test.com)
- Charlie Brown (charlie@test.com)
- ... etc

All with password: `Test123!`

## Insert Test Conversations

### 1. SpaceX Starship Conversation (RECOMMENDED for AI Testing)

**Optimized for testing all 5 AI features:**

```bash
node insert-spacex-conversation.js
```

Creates group **"SpaceX Starship"** with 50 messages about rocket development:
- ✅ **Feature 1 (Summarization):** Full technical discussion
- ✅ **Feature 2 (Action Items):** Multiple tasks with deadlines
- ✅ **Feature 3 (Smart Search):** Technical terms (Raptor, Mach 15, heat shield)
- ✅ **Feature 4 (Priority Detection):** 8 URGENT/BLOCKER messages 🚨
- ✅ **Feature 5 (Decision Tracking):** 5 explicit decisions with confirmations

**Best for:** Testing Priority Detection and Decision Tracking features!

---

### 2. Crypto Project Conversation

```bash
node insert-crypto-conversation.js
```

Creates group **"CryptoProject"** with 52 messages about crypto trading feature:
- Technical discussion about API integration
- Security and compliance planning
- Good for basic AI testing

**Best for:** General testing of Summarization and Action Items.

---

## Testing AI Features

After inserting conversations:

1. Open Synapse Android app
2. Navigate to the group ("SpaceX Starship" or "CryptoProject")
3. Tap the AI button (⭐) in the toolbar
4. Select AI feature to test:
   - Thread Summarization
   - Action Item Extraction
   - **Priority Detection** (NEW - try on SpaceX conversation!)
   - **Decision Tracking** (NEW - try on SpaceX conversation!)
   - Custom Instructions

## Usage

After creating users, you can login to the Android app with any of these credentials.

**Note:** Add `service-account-key.json` to `.gitignore` (already done).

