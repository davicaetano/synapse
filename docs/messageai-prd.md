# Synapse - Product Requirements Document

## 1. Project Overview

### Vision
Build a production-quality messaging application for remote teams that combines WhatsApp-level reliability with intelligent AI features that make distributed work more productive.

**Product Name**: Synapse  
**Tagline**: "Where teams connect intelligently"

### Goals
- **Week 1 Sprint**: Deliver a fully functional messaging app with AI-powered features tailored for remote team professionals
- **Technical Excellence**: Demonstrate mastery of real-time systems, offline-first architecture, and AI agent integration
- **User Value**: Solve real pain points that remote teams face daily - information overload, missed context, and coordination overhead

### Success Metrics
- **MVP (24 hours)**: Core messaging infrastructure working reliably
- **Early Submission (4 days)**: All 5 required AI features functional
- **Final (7 days)**: Proactive Assistant deployed and demonstrated in realistic scenarios

### Why This Matters
Remote teams lose 2-3 hours daily to context switching, searching for information, and coordinating schedules. Synapse makes communication intelligent - automatically surfacing what matters, extracting actionable insights, and proactively helping teams stay aligned.

---

## 2. User Persona: Remote Team Professional

### Profile
**Name**: Sarah Chen  
**Role**: Senior Product Manager at a distributed tech company  
**Team**: 8 engineers across 4 time zones (SF, NYC, Berlin, Bangalore)  
**Work Style**: Asynchronous-first, high context switching, 50+ Slack messages/day

### Core Pain Points

#### 1. Drowning in Message Threads
- "I come back from a 2-hour meeting and have 147 unread messages across 5 channels"
- "I need to piece together what was decided while I was offline"
- **Impact**: 30-45 minutes daily just catching up on threads

#### 2. Missing Critical Information
- "Someone @mentioned me in a thread 3 days ago and I never saw it"
- "The CEO asked a question buried in a 50-message thread"
- **Impact**: Delayed responses, dropped balls, perception of being unresponsive

#### 3. Constant Context Switching
- "I'm reviewing a PR, get a Slack ping, lose my train of thought"
- "Which thread was that bug report in? Search is useless with generic terms"
- **Impact**: Fragmented focus, 23 minutes to regain deep work state

#### 4. Time Zone Coordination Hell
- "Let's schedule a call" → 15 messages back and forth finding a time
- "Who's available right now to review this?"
- **Impact**: Simple coordination takes hours, urgent issues blocked

### User Needs (Prioritized)
1. **See what matters**: Instantly understand what needs attention
2. **Find anything fast**: Search that understands context, not just keywords
3. **Extract action items**: Know what I need to do without re-reading everything
4. **Automated coordination**: AI handles scheduling, I just confirm
5. **Decision tracking**: Quick recall of "why did we decide X?"

---

## 3. Core Messaging Features

### 3.1 One-on-One Chat
**What it does**: Two users can send text messages in real-time

**User Stories**:
- As a user, I can start a new chat with any team member
- As a user, I can see when the other person is typing
- As a user, I can see when my message was delivered and read
- As a user, I can see the other person's online/offline status
- As a user, I can send messages even when offline (queued for delivery)

**Non-Functional Requirements**:
- Message delivery latency: <500ms on good network (4G/WiFi)
- Offline messages sync within 5 seconds of reconnection
- Messages persist indefinitely (user can scroll back to first message)

### 3.2 Group Chat
**What it does**: 3+ users can communicate in a shared conversation

**User Stories**:
- As a user, I can create a group with multiple members
- As a user, I can see who is in the group
- As a user, I can see when any member is online
- As a user, I can see who has read each message
- As a user, I can send messages to the group even when offline

**Non-Functional Requirements**:
- Support up to 50 members per group (MVP: 10)
- Message delivery to all online members: <1 second
- Read receipts update within 2 seconds

### 3.3 Real-Time Delivery
**What it does**: Messages appear instantly for online recipients

**User Stories**:
- As a user, when I send a message, it appears immediately in my chat (optimistic update)
- As a user, I see a visual indicator when my message is sending vs sent vs delivered
- As a recipient, I see new messages appear without refreshing
- As a user, I can receive messages while the app is in the background

**Non-Functional Requirements**:
- Optimistic updates: message appears in UI <100ms after send
- Real-time listener establishes connection within 2 seconds of app open
- Handle network transitions gracefully (WiFi ↔ 4G)

### 3.4 Offline Support
**What it does**: App works without internet, syncs when connection returns

**User Stories**:
- As a user, I can read all my past messages when offline
- As a user, I can send messages when offline (queued locally)
- As a user, when I go back online, my queued messages send automatically
- As a user, I receive messages that were sent while I was offline
- As a user, the app clearly indicates when I'm offline

**Non-Functional Requirements**:
- All messages cached locally by Firestore (offline persistence built-in)
- Offline mode works for 7+ days (Firestore cache limit: configurable, default 40MB)
- Queue up to 500 messages locally while offline
- Sync queued messages within 30 seconds of reconnection

### 3.5 Media Support (MVP: Images)
**What it does**: Users can send and receive images

**User Stories**:
- As a user, I can attach an image from my gallery
- As a user, I can take a photo with my camera and send it
- As a user, I can view images in full-screen
- As a user, images load quickly (with progressive loading)

**Non-Functional Requirements**:
- Image upload: compress to <2MB before sending
- Support JPEG, PNG formats
- Thumbnail loads in <1 second on 4G

### 3.6 Presence & Typing Indicators
**What it does**: Real-time awareness of other users' activity

**User Stories**:
- As a user, I can see if someone is "online" (green dot)
- As a user, I can see "last seen" timestamp if someone is offline
- As a user, I see "typing..." when someone is composing a message
- As a user, my typing indicator disappears after 3 seconds of inactivity

**Non-Functional Requirements**:
- Presence updates: <1 second latency
- Typing indicator: appears within 500ms of user typing
- "Last seen" accurate within 5 seconds

### 3.7 Push Notifications
**What it does**: Users receive notifications for new messages when app is backgrounded

**User Stories**:
- As a user, I receive a notification when someone sends me a message
- As a user, tapping the notification opens the chat
- As a user, I see the sender's name and message preview
- As a user, I can disable notifications per chat

**Non-Functional Requirements**:
- Notification delivery: <5 seconds (via FCM)
- Works when app is closed, backgrounded, or foreground
- Notification includes sender name + first 50 chars of message

---

## 4. AI Features (5 Required)

### 4.1 Thread Summarization

**What it does**: AI generates concise summaries of long conversation threads

**User Stories**:
- As a user, I can request a summary of the last N messages in a chat
- As a user, I can request a summary of messages since I was last online
- As a user, the summary highlights key points, decisions, and action items
- As a user, the summary is in bullet points (scannable)
- As a user, I can request different summary lengths (brief, detailed)

**Example Scenario**:
> Sarah returns from a 3-hour flight. Her team chat has 89 new messages discussing a production incident, root cause analysis, and mitigation plan. She taps "Summarize since I was offline" and gets:
> 
> **Summary (89 messages, 3h 24m)**
> - Production API experienced 23% error rate spike at 14:32 UTC
> - Root cause: database connection pool exhaustion (max 50 connections reached)
> - Fix deployed at 15:47 UTC, error rate returned to baseline
> - Action items: [see section 4.2]
> - Decision: Increase connection pool to 100, add monitoring alerts

**Success Criteria**:
- Summary generated in <10 seconds for 100-message thread
- Accuracy: captures 90%+ of key information (human eval)
- No hallucinations (doesn't invent facts not in messages)

**Technical Notes**:
- Uses LLM (Claude/GPT-4) with conversation history as context
- RAG pipeline: retrieve relevant messages, chunk if thread is very long
- Prompt engineering: "You are summarizing a work chat for a busy PM who was offline..."

---

### 4.2 Action Item Extraction

**What it does**: AI identifies tasks, todos, and commitments from conversations

**User Stories**:
- As a user, I can extract action items from any thread
- As a user, action items show WHO is responsible (if mentioned)
- As a user, action items show WHAT needs to be done
- As a user, action items include WHEN if a deadline was mentioned
- As a user, I can mark action items as complete

**Example Scenario**:
> During standup chat:
> - "Alex: I'll review the PR by EOD"
> - "Sarah: Can someone test the staging deploy before 3pm?"
> - "Ben: Sure, I'll test it this afternoon"
> - "Alex: Reminder - we need to update the docs"
> 
> AI extracts:
> - [ ] **Alex**: Review PR (Deadline: EOD today)
> - [ ] **Ben**: Test staging deploy (Deadline: 3pm today)
> - [ ] **Team**: Update documentation (No deadline)

**Success Criteria**:
- Extract 95%+ of explicit commitments ("I'll do X")
- Correctly identify assignee 90%+ of the time
- Parse deadline expressions (EOD, tomorrow, Friday, 3pm)

**Technical Notes**:
- LLM with function calling to extract structured data
- Output schema: {task: string, assignee: string, deadline: string, raw_message: string}

---

### 4.3 Smart Search

**What it does**: Semantic search that understands intent, not just keywords

**User Stories**:
- As a user, I can search for messages using natural language
- As a user, search understands synonyms (e.g., "bug" finds "issue", "problem")
- As a user, search understands context (e.g., "the API change" finds relevant thread)
- As a user, I can search by concept (e.g., "why did we choose PostgreSQL")
- As a user, search results are ranked by relevance

**Example Scenarios**:

**Query**: "authentication issue last week"
- Finds: Messages discussing login problems, 401 errors, JWT expiration (even if exact phrase not used)

**Query**: "who volunteered to present at all-hands"
- Finds: Message from Lisa saying "I can do the demo on Friday"

**Query**: "decision about database"
- Finds: Thread where team discussed Postgres vs MySQL and why Postgres was chosen

**Success Criteria**:
- Return relevant results for 85%+ of natural language queries
- Search latency: <2 seconds
- Better than keyword search (user testing validation)

**Technical Notes**:
- RAG with vector embeddings (embed messages, embed query, cosine similarity)
- Option 1: Embed messages at write-time, store in vector DB
- Option 2: On-demand embedding + semantic ranking
- LLM re-ranks top results based on query intent

---

### 4.4 Priority Message Detection

**What it does**: AI automatically flags messages that need urgent attention

**User Stories**:
- As a user, priority messages appear highlighted in my chat list
- As a user, I see a "Priority" badge on important messages
- As a user, I can see WHY a message was marked priority
- As a user, I receive notifications for priority messages even if I've muted the chat
- As a user, I can provide feedback if priority detection is wrong (learn over time)

**Priority Criteria** (AI evaluates):
1. **Direct question to me**: "@Sarah can you review this?"
2. **Urgency indicators**: "urgent", "ASAP", "blocker", "production down"
3. **Decision needed**: "Should we go with option A or B?"
4. **Time-sensitive**: "Meeting in 10 minutes", "deadline today"
5. **From leadership**: Message from CEO, VP, direct manager

**Example Scenario**:
> Sarah has 47 unread messages across 3 chats. AI flags:
> 
> 🔴 **PRIORITY** (Engineering chat): "Production API is down, need Sarah's approval to rollback" (Reason: Urgent + Direct mention + Production incident)
> 
> 🔴 **PRIORITY** (Product chat): "Sarah - do we move forward with design option B? Need decision by EOD" (Reason: Direct question + Decision needed + Deadline)
> 
> Regular message: "Anyone want to grab lunch?" (No priority flags)

**Success Criteria**:
- Precision: 80%+ of flagged messages are actually important (avoid alert fatigue)
- Recall: Catch 90%+ of truly urgent messages (don't miss critical stuff)
- False positive rate: <10%

**Technical Notes**:
- LLM classifier with few-shot examples
- Input: message text, sender role, channel context, time
- Output: {priority: boolean, confidence: float, reason: string}

---

### 4.5 Decision Tracking

**What it does**: AI identifies and logs decisions made in conversations

**User Stories**:
- As a user, I can see a list of all decisions made in a chat
- As a user, each decision includes: what was decided, when, who was involved
- As a user, I can see the original message thread that led to the decision
- As a user, I can search decisions (e.g., "why did we choose AWS")
- As a user, I can manually mark a message as a decision if AI missed it

**Example Scenario**:
> Team discusses database options over 23 messages. AI detects:
> 
> **Decision**: Use PostgreSQL for primary database
> - **When**: March 15, 2025 at 2:34 PM
> - **Participants**: Sarah, Alex, Ben
> - **Rationale**: Better JSON support, team has more experience, existing tooling
> - **Source**: [Link to message thread]

**Decision Indicators** (AI looks for):
- Phrases: "let's go with", "we decided", "final call", "agreed on"
- Consensus: Multiple people confirming the same choice
- Closure: "sounds good", "approved", "done"

**Success Criteria**:
- Capture 85%+ of explicit decisions
- Low false positive rate: <15% (not every message is a decision)
- Searchable: find decision by keyword in <2 seconds

**Technical Notes**:
- LLM analyzes thread context to detect decision points
- Extract structured data: {decision: string, rationale: string, participants: array, timestamp: date}
- Store in Firestore collection: "decisions/{chatId}/decisions/{decisionId}"

---

## 5. Advanced AI Feature: Proactive Assistant

### Overview
The Proactive Assistant is an AI agent that actively monitors conversations and proactively suggests actions without being explicitly asked. It detects scheduling needs, coordination requests, and automatically drafts solutions.

### 5.1 Auto-Suggest Meeting Times

**What it does**: When team members discuss scheduling a meeting, AI automatically suggests available times

**User Stories**:
- As a user, when someone says "let's schedule a call", AI suggests 3 time slots
- As a user, AI considers everyone's time zones
- As a user, AI avoids suggesting times outside working hours (9am-6pm local time)
- As a user, I can accept a suggested time with one tap
- As a user, AI sends calendar invites when a time is accepted

**Example Scenario**:
> **Alex**: "We need to sync on the API redesign. Sarah and Ben, when are you free?"
> 
> 🤖 **Proactive Assistant**: 
> "I can help schedule this! Based on everyone's time zones (Alex: SF, Sarah: NYC, Ben: Berlin), here are 3 options:
> - Tomorrow (Tue) at 10am PT / 1pm ET / 7pm CET
> - Wednesday at 2pm PT / 5pm ET / 11pm CET ❌ (too late for Ben)
> - Thursday at 9am PT / 12pm ET / 6pm CET ✅ Recommended
> 
> React with ✅ to confirm Thursday 9am PT."

**Trigger Conditions** (AI detects):
- Keywords: "schedule", "meeting", "sync", "call", "catch up"
- Multiple participants mentioned
- No specific time proposed yet

**Success Criteria**:
- Activates on 80%+ of scheduling discussions
- Suggested times are actually feasible (not during known conflicts)
- Users accept AI suggestions 60%+ of the time (validation that suggestions are good)

**Technical Notes**:
- Agent has tools: `get_timezones()`, `find_common_availability()`, `create_calendar_invite()`
- Multi-step reasoning: detect scheduling need → identify participants → calculate time zones → suggest slots
- Future enhancement: integrate with actual calendars (Google Calendar API)

---

### 5.2 Detect Scheduling Needs

**What it does**: AI recognizes implicit coordination needs and offers to help

**User Stories**:
- As a user, when someone asks "is anyone free to pair on this?", AI detects coordination need
- As a user, AI suggests solutions without being asked
- As a user, I can dismiss AI suggestions if not helpful
- As a user, AI learns from my feedback (if I dismiss often, it becomes less proactive)

**Example Scenarios**:

**Scenario 1: Pairing Request**
> **Sarah**: "This bug is tricky, could use another set of eyes"
> 
> 🤖 **Assistant**: "I see you need help! Here's who's online and available right now:
> - **Ben** (online, last message 2 min ago)
> - **Lisa** (online, currently in #design-chat)
> 
> Would you like me to ping them?"

**Scenario 2: Deadline Coordination**
> **Alex**: "We need to ship this by Friday. Who can help with testing?"
> 
> 🤖 **Assistant**: "I can help coordinate! Based on recent activity:
> - **Ben**: Has testing experience, was involved in last release
> - **Maria**: Mentioned she has capacity this week
> 
> Should I draft a message asking for their help?"

**Trigger Conditions**:
- Questions about availability ("who's free?", "anyone available?")
- Requests for help ("need help with X", "can someone...")
- Implicit urgency ("need to ship by...", "blocker")

**Success Criteria**:
- Detect 70%+ of coordination needs
- Suggestions are helpful 75%+ of the time (user feedback)
- Low annoyance factor: users don't dismiss as spam

**Technical Notes**:
- Agent monitors all messages in real-time
- Context awareness: knows who's online (from Realtime DB), recent activity, expertise
- Multi-step reasoning: detect need → identify helpers → draft message → present to user

---

### 5.3 Proactive Reminders

**What it does**: AI reminds users about commitments made in chat

**User Stories**:
- As a user, if I said "I'll do X by Friday" and it's Thursday EOD, AI reminds me
- As a user, AI sends a gentle ping in the chat where the commitment was made
- As a user, I can mark the reminder as "done" or "snoozed"

**Example**:
> (Thursday 5pm)
> 🤖 **Assistant**: "@Sarah, friendly reminder: Yesterday you mentioned you'd review the PR by EOD today. The PR is still open. Would you like me to notify the team if you need more time?"

**Trigger Conditions**:
- User made a commitment with a deadline
- Deadline is approaching (within 2 hours)
- Action item not marked complete

**Success Criteria**:
- Never miss a deadline that was mentioned in chat
- Reminders are timely (not too early, not too late)
- Users find this helpful, not annoying (measured by feedback)

---

## 6. Technical Architecture (High-Level)

### 6.1 System Components

```
┌─────────────────────────────────────────────────────────┐
│                     Android App                         │
│              (Kotlin + Jetpack Compose)                 │
└────┬──────────────────────┬─────────────────────┬───────┘
     │                      │                     │
     ↓                      ↓                     ↓
┌─────────────┐    ┌─────────────────┐    ┌──────────────┐
│  Firestore  │    │  Realtime DB    │    │  Python API  │
│             │    │                 │    │  (FastAPI)   │
│ • Messages  │    │ • Presence      │    │              │
│ • Chats     │    │ • Typing        │    │ • AI Agent   │
│ • Users     │    │ • Last seen     │    │ • LangChain  │
│ • Groups    │    │                 │    │ • Claude API │
└─────────────┘    └─────────────────┘    └──────┬───────┘
                                                  │
                                          ┌───────┴────────┐
                                          │ Firebase Admin │
                                          │ SDK (read msgs)│
                                          └────────────────┘
```

### 6.2 Data Flow Examples

**Sending a Message**:
```
1. User types message in Android app
2. App immediately shows message (optimistic update)
3. App writes to Firestore: /chats/{chatId}/messages/{messageId}
4. Firestore propagates to all listeners (real-time)
5. Recipient's app receives new message via listener
6. Recipient sees message appear
7. Recipient's app updates read receipt in Firestore
```

**AI Summarization Request**:
```
1. User taps "Summarize" button
2. Android calls: POST api.messageia.com/ai/summarize
   Body: {chatId: "abc123", since: timestamp}
3. Python API:
   a. Fetches messages from Firestore (using Firebase Admin SDK)
   b. Builds context for LLM
   c. Calls Claude API with prompt
   d. Processes response
4. API returns: {summary: "...", key_points: [...]}
5. Android displays summary in UI
6. (Optional) Android saves summary to Firestore for caching
```

**Presence Update**:
```
1. User opens app
2. Android writes to Realtime DB: /presence/{userId}/online = true
3. Android sets disconnect handler: onDisconnect().setValue(false)
4. Other users listening to /presence/{userId} get real-time update
5. User closes app → disconnect handler triggers automatically
6. Online status changes to false
```

### 6.3 Technology Stack

**Android**:
- Language: Kotlin
- UI: Jetpack Compose
- Local Storage: 
  - **Messages**: Firestore offline cache (built-in, no Room needed)
  - **Other data**: Room (user preferences, cached images, app state)
- Networking: Retrofit (for Python API calls)
- Firebase SDK: Firestore, Realtime Database, Auth, FCM, Storage
- Image Loading: Coil

**Backend (Python API)**:
- Framework: FastAPI
- AI Agent: LangChain
- LLM: Anthropic Claude (or OpenAI GPT-4)
- Firebase: Firebase Admin SDK (to read Firestore from backend)
- Deployment: **Render.com** (confirmed - works well, free tier available)

**Firebase Services**:
- **Firestore**: Messages, chats, users, groups (structured data)
- **Realtime Database**: Presence, typing indicators (ephemeral state)
- **Authentication**: User accounts (email/password for MVP)
- **Cloud Messaging (FCM)**: Push notifications
- **Storage**: Image uploads

**AI & Agent**:
- **LLM Provider**: Anthropic Claude Sonnet or OpenAI GPT-4
- **Agent Framework**: LangChain (recommended), AI SDK (Vercel), or OpenAI Swarm
- **RAG**: Vector search (optional: Pinecone, Weaviate) or simple retrieval from Firestore

### 6.4 Development Mode & Environment Switching

**Overview**: Debug builds include a hidden Developer Menu that allows switching between Firebase environments (Dev vs Production) without rebuilding the app. This enables flexible testing and debugging workflows.

**Access Method**:
- Navigate to Settings screen
- Tap 7 times on the app logo or version number
- Developer Menu appears as a dialog/bottom sheet

**Developer Menu Features**:

```
┌─────────────────────────────┐
│    🛠️  Developer Menu        │
├─────────────────────────────┤
│                             │
│  Firebase Environment:      │
│  ○ Dev (synapse-dev)        │
│  ● Prod (synapse-prod)      │
│                             │
│  Backend API:               │
│  ○ Local (localhost:8000)   │
│  ○ Dev (synapse-dev.onrender.com)     │
│  ● Prod (synapse.onrender.com)        │
│                             │
│  Debug Tools:               │
│  [View Logs]                │
│  [Clear Firestore Cache]    │
│  [Simulate Offline Mode]    │
│                             │
│  Current Config:            │
│  Firebase: synapse-prod     │
│  User: alice@test.com       │
│                             │
│  [ Apply & Restart ]        │
└─────────────────────────────┘
```

**Environment Configurations**:

**Dev Environment**:
- Firebase Project: `synapse-dev`
- Test data and users
- More permissive security rules (easier debugging)
- Backend API: Dev instance on Render

**Production Environment**:
- Firebase Project: `synapse-prod`
- Real/clean data
- Strict security rules
- Backend API: Production instance on Render

**Implementation Notes**:

1. **Config Storage**: Selected environment stored in SharedPreferences/DataStore
2. **Firebase Initialization**: App uses stored environment preference to initialize Firebase with correct credentials
3. **Environment Switching**: When user changes environment, app prompts to restart to apply changes
4. **Build Variants**:
   - **Debug builds**: Developer Menu enabled, can access both Dev and Prod configs
   - **Release builds**: Developer Menu completely disabled (code stripped), only Prod config available

**Configuration Files**:

```
android/app/src/
├── debug/
│   └── res/values/
│       └── firebase_configs.xml    # Contains both Dev + Prod configs
└── release/
    └── res/values/
        └── firebase_configs.xml    # Contains only Prod config
```

**Use Cases**:

1. **Development**: Use Dev environment for daily development with test data
2. **Pre-deployment Testing**: Switch to Prod to validate features work with production Firebase before final release
3. **Debugging Production Issues**: Use debug build with Prod environment to investigate reported issues
4. **Demo Recording**: Use debug build with Prod environment for clean data in demo videos

**Security**:
- Developer Menu only exists in debug builds (`BuildConfig.DEBUG == true`)
- Release builds have no way to access Dev environment
- API keys for both environments stored securely in build configs (not in code)

```
Synapse/
├── android/
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/synapse/
│   │   │   │   │   ├── ui/              # Compose UI screens
│   │   │   │   │   ├── data/            # Repositories, data sources
│   │   │   │   │   │   ├── firestore/   # Firestore (messages, chats)
│   │   │   │   │   │   ├── realtime/    # Realtime DB (presence)
│   │   │   │   │   │   └── local/       # Room (preferences, cached images)
│   │   │   │   │   ├── domain/          # Business logic, use cases
│   │   │   │   │   ├── network/         # Retrofit API client
│   │   │   │   │   └── MainActivity.kt
│   │   │   │   ├── res/                 # Resources (layouts, strings)
│   │   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── gradle/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── backend/
│   ├── api/
│   │   ├── main.py                      # FastAPI app entry point
│   │   ├── agent/
│   │   │   ├── proactive_assistant.py   # Proactive AI agent logic
│   │   │   ├── summarizer.py            # Thread summarization
│   │   │   ├── action_extractor.py      # Extract action items
│   │   │   ├── search.py                # Semantic search
│   │   │   └── priority_detector.py     # Priority message detection
│   │   ├── firestore_client.py          # Firebase Admin SDK wrapper
│   │   ├── models.py                    # Pydantic models
│   │   └── routers/
│   │       ├── ai.py                    # /ai/* endpoints
│   │       └── health.py                # Health check
│   ├── requirements.txt
│   ├── .env.example
│   └── README.md
│
├── firebase/
│   ├── firestore.rules                  # Firestore security rules
│   ├── firestore.indexes.json           # Composite indexes
│   ├── database.rules.json              # Realtime DB security rules
│   ├── storage.rules                    # Storage security rules
│   └── firebase.json                    # Firebase config
│
├── docs/
│   ├── PRD.md                           # This document
│   ├── ARCHITECTURE.md                  # Detailed architecture diagrams
│   ├── API.md                           # API documentation
│   └── SETUP.md                         # Setup instructions
│
└── README.md                            # Root README with quick start
```

### Component Responsibilities

**Android (`/android`)**:
- All mobile UI/UX
- Real-time listeners for Firestore (messages) and Realtime Database (presence)
- **Firestore offline cache**: Messages automatically cached, no Room needed
- **Room database**: Only for user preferences, cached images, app state
- Push notification handling
- Calling Python API for AI features

**Backend (`/backend`)**:
- AI agent logic (all 5 required + proactive assistant)
- LLM API calls (Claude/GPT-4)
- Reading messages from Firestore for AI processing
- Exposing REST API for Android to call

**Firebase (`/firebase`)**:
- Security rules (who can read/write what)
- Database indexes for query performance
- Configuration files

**Docs (`/docs`)**:
- Product documentation
- Technical architecture
- Setup guides

---

## 8. Development Phases

### 8.1 MVP - 24 Hours (Hard Gate)

**Goal**: Prove messaging infrastructure is rock-solid

**Must Have**:
- ✅ One-on-one chat with 2+ users
- ✅ Real-time message delivery
- ✅ Message persistence (survives app restart)
- ✅ Optimistic UI updates
- ✅ Online/offline status indicators
- ✅ Message timestamps
- ✅ User authentication (Firebase Auth)
- ✅ Basic group chat (3+ users)
- ✅ Message read receipts
- ✅ Push notifications (at least foreground)
- ✅ Deployed backend + local Android build

**Success Criteria**:
- Two Android devices can chat in real-time
- Messages never get lost (test: force close app mid-send)
- Offline mode works (test: airplane mode → send message → reconnect)

**Out of Scope for MVP**:
- AI features
- Image sending
- Polish/animations
- Complex group features

---

### 8.2 Early Submission - 4 Days

**Goal**: All 5 required AI features working with real use cases

**Must Have** (in addition to MVP):
- ✅ Thread summarization (tested with 50+ message thread)
- ✅ Action item extraction (tested with standup chat)
- ✅ Smart search (tested with natural language queries)
- ✅ Priority message detection (tested with urgent scenarios)
- ✅ Decision tracking (tested with decision-making thread)
- ✅ Python API deployed and callable from Android
- ✅ Basic error handling for AI failures

**Success Criteria**:
- Each AI feature demonstrated with realistic scenario
- AI responses are accurate (no hallucinations)
- Latency acceptable (<10s for complex operations)

**Nice to Have**:
- Image sending working
- Proactive Assistant (basic version)

---

### 8.3 Final Submission - 7 Days

**Goal**: Proactive Assistant deployed, polished demo, complete documentation

**Must Have** (in addition to Early):
- ✅ Proactive Assistant: auto-suggest meeting times
- ✅ Proactive Assistant: detect scheduling needs
- ✅ Proactive reminders for commitments
- ✅ Polished UI/UX (animations, loading states, error messages)
- ✅ Deployed Android app (APK or TestFlight)
- ✅ Demo video (5-7 minutes) showing all features
- ✅ Complete documentation (README, setup guide)
- ✅ Persona Brainlift (1-page explanation)

**Success Criteria**:
- Proactive Assistant feels genuinely helpful (not annoying)
- Demo video tells compelling story of remote team using MessageAI
- Documentation allows someone else to run the project

**Nice to Have**:
- Advanced RAG (vector embeddings)
- Agent learns from user feedback
- Multiple chat themes

---

## 9. Testing Scenarios

### 9.1 Core Messaging Tests

**Test 1: Real-Time Sync**
- Setup: 2 Android devices, both online
- Action: Device A sends message
- Expected: Device B sees message within 1 second

**Test 2: Offline → Online**
- Setup: Device B goes offline (airplane mode)
- Action: Device A sends 5 messages
- Action: Device B comes back online
- Expected: Device B receives all 5 messages within 10 seconds

**Test 3: Optimistic Updates**
- Setup: Device A online
- Action: Send message with slow network (throttled)
- Expected: Message appears immediately in sender's UI, then shows "sending..." indicator

**Test 4: App Lifecycle**
- Setup: Device A in chat
- Action: Home button → app backgrounds
- Action: Device B sends message
- Expected: Device A gets push notification

**Test 5: Force Quit & Persistence**
- Setup: Device A in active chat
- Action: Send 3 messages
- Action: Force quit app (swipe away from recent apps)
- Action: Reopen app
- Expected: All 3 messages visible, chat state preserved

**Test 6: Poor Network Conditions**
- Setup: Simulate 3G network (Android Dev Tools or network throttling)
- Action: Send 10 messages rapidly
- Expected: All messages eventually deliver, no duplicates, order preserved

**Test 7: Group Chat**
- Setup: 3 devices in group chat
- Action: Device A sends message
- Expected: Devices B and C both receive, read receipts show who read

**Test 8: Typing Indicators**
- Setup: 2 devices in chat
- Action: Device A starts typing
- Expected: Device B sees "typing..." within 1 second
- Action: Device A stops typing for 3 seconds
- Expected: "typing..." disappears on Device B

---

### 9.2 AI Feature Tests

**Test 9: Thread Summarization**
- Setup: Chat with 50+ messages discussing a project decision
- Action: Request summary
- Expected: 
  - Summary generated in <10 seconds
  - Captures key points (decision made, rationale, action items)
  - No hallucinated information
  - Readable format (bullet points)

**Test 10: Action Item Extraction**
- Setup: Chat with 3 people making commitments
  - "I'll review the PR by tomorrow"
  - "Can someone test this? I'll do it - Sarah"
  - "Reminder: update docs"
- Action: Extract action items
- Expected:
  - 3 action items extracted
  - Correct assignees identified
  - Deadlines parsed correctly ("tomorrow" → actual date)

**Test 11: Smart Search**
- Setup: 500+ messages across multiple topics
- Test Query: "authentication bug last week"
- Expected: Finds messages about login issues, even if exact phrase not used
- Test Query: "why did we choose PostgreSQL"
- Expected: Finds decision-making thread about database selection

**Test 12: Priority Detection**
- Setup: 20 unread messages
- Include: 
  - "@Sarah production is down" (should flag)
  - "Anyone want coffee?" (should NOT flag)
  - "Need your approval by EOD - CEO" (should flag)
- Expected:
  - 2-3 messages flagged as priority
  - Explanations for why (urgency, direct mention, deadline)

**Test 13: Decision Tracking**
- Setup: 30-message thread discussing tech stack choice
- Decision point: "Okay, let's go with React Native"
- Action: View decision log
- Expected:
  - Decision captured with timestamp
  - Participants listed
  - Rationale extracted
  - Link to original message thread

**Test 14: Proactive Assistant - Meeting Scheduling**
- Setup: Group chat with 3 users in different time zones
- Trigger: "Let's schedule a sync call - Alex, Sarah, Ben free?"
- Expected:
  - AI activates within 5 seconds
  - Suggests 3 time slots
  - Considers all time zones
  - Provides clear recommendation

**Test 15: Proactive Assistant - Detect Coordination Needs**
- Setup: Chat where someone asks "is anyone free to pair on this bug?"
- Expected:
  - AI detects coordination need
  - Suggests who's online and available
  - Offers to draft message or ping people

---

### 9.3 Edge Cases & Error Handling

**Test 16: Network Timeout**
- Setup: Send AI request, then disconnect network
- Expected: Android shows error message, allows retry

**Test 17: LLM Rate Limit**
- Setup: Send 10 AI requests rapidly
- Expected: Graceful error handling, queue requests if needed

**Test 18: Empty Chat Summary**
- Setup: Chat with only 2 messages
- Action: Request summary
- Expected: "Not enough messages to summarize" or simple 1-line summary

**Test 19: Message with No Text**
- Setup: Send image-only message
- Action: Try to summarize
- Expected: AI handles gracefully (mentions image was sent)

**Test 20: Malformed AI Response**
- Setup: LLM returns invalid JSON or unexpected format
- Expected: Error caught, user sees "AI is having trouble, please try again"

---

## 10. Deployment Strategy

### 10.1 Android App Deployment

**MVP (24h) - Local Testing**:
- Build debug APK: `./gradlew assembleDebug`
- Install on physical devices via USB
- Test on at least 2 devices

**Early/Final Submission - Wider Distribution**:
- **Option A**: Upload to Google Play Console (Internal Testing track)
- **Option B**: Build release APK and share download link
- **Option C**: Use Firebase App Distribution

**Requirements**:
- Signed APK (release builds)
- Icon and app name configured
- Proper permissions declared in manifest
- ProGuard rules configured (if using obfuscation)

---

### 10.2 Backend Deployment

**Python API Deployment: Render.com**

Render is the chosen platform for its reliability and ease of use.

**Deployment Steps**:
```
1. Connect GitHub repo to Render
2. Create new Web Service
3. Select backend/api folder as root directory
4. Configure:
   - Environment: Python 3.11+
   - Build command: pip install -r requirements.txt
   - Start command: uvicorn main:app --host 0.0.0.0 --port $PORT
5. Add environment variables (see below)
6. Deploy (automatic on git push to main)
```

**Environment Variables Required**:
```
ANTHROPIC_API_KEY=sk-ant-...
FIREBASE_CREDENTIALS=<service-account-json>
FIREBASE_PROJECT_ID=synapse-xxx
ALLOWED_ORIGINS=*
PORT=10000
```

**Render Free Tier Limits**:
- 750 hours/month (enough for this project)
- Spins down after 15 min of inactivity (cold start: ~30s)
- 512MB RAM (sufficient for FastAPI + LangChain)

**Health Check**:
- Configure Render health check: GET /health
- Auto-restart on failures

---

### 10.3 Firebase Configuration

**Setup Steps**:
1. Create Firebase project in console
2. Enable Firestore, Realtime Database, Auth, Storage, FCM
3. Download `google-services.json` for Android
4. Generate service account key for Python backend
5. Deploy security rules: `firebase deploy --only firestore:rules,database:rules`

**Security Rules - Must Configure**:
- Firestore: users can only read/write their own chats
- Realtime DB: users can only write their own presence
- Storage: users can only upload to their own folders

---

### 10.4 CI/CD (Optional but Recommended)

**GitHub Actions Workflow**:
```yaml
# Pseudo-code structure
on: push to main branch
jobs:
  - Build Android APK
  - Run Android tests
  - Deploy Python API to Render
  - Deploy Firebase rules
```

Benefits:
- Automatic deployments on git push
- Catch build failures early
- Consistent deployment process

---

## 11. Non-Functional Requirements

### 11.1 Performance

**Message Delivery**:
- Real-time latency: <500ms on WiFi/4G
- Offline message sync: within 5 seconds of reconnection
- App cold start: <3 seconds to show chat list

**AI Features**:
- Summarization (50 messages): <10 seconds
- Action extraction (20 messages): <5 seconds
- Search query: <2 seconds to show results
- Priority detection: <1 second (should not block message display)

**UI Responsiveness**:
- Scroll performance: 60fps on mid-range devices
- Typing latency: <50ms from keypress to character appearing
- Image loading: thumbnail in <1 second, full image <3 seconds

---

### 11.2 Scalability

**MVP Targets** (Week 1):
- Support: 10 concurrent users
- Messages: up to 1,000 messages per chat
- Chats: 50 chats per user
- Groups: up to 10 members per group

**Production Targets** (Future):
- 10,000+ concurrent users
- Unlimited message history
- Groups up to 100 members

---

### 11.3 Reliability

**Uptime**:
- Python API: 99% uptime (use health checks, auto-restart)
- Firebase: 99.95% (handled by Google)

**Data Durability**:
- Zero message loss (Firestore guarantees persistence)
- **Messages**: Firestore offline cache (automatic, survives app restart)
- **User data**: Room database (preferences, cached images - survives app uninstall if configured)

**Error Recovery**:
- Network failures: automatic retry with exponential backoff
- LLM failures: graceful degradation, show error message
- Partial failures: don't crash app, log error

---

### 11.4 Security

**Authentication**:
- Firebase Auth with email/password (MVP)
- Session management handled by Firebase SDK
- Tokens automatically refreshed

**Data Access**:
- Users can only read chats they're part of (Firestore rules)
- API validates user identity (Firebase Auth token)
- No public read access to any data

**API Security**:
- HTTPS only (no HTTP)
- API keys stored in environment variables (never in code)
- Rate limiting on API endpoints (100 req/min per user)

**Future Considerations** (Out of Scope for Week 1):
- End-to-end encryption
- Two-factor authentication
- Message deletion/editing with audit trail

---

### 11.5 Secrets Management & Git Security

**🚨 CRITICAL: Never commit sensitive files to GitHub**

The following files contain sensitive credentials and MUST be in `.gitignore`:

**Android**:
```
# NEVER commit these:
android/app/google-services.json           # Firebase config (contains API keys)
android/app/src/debug/google-services.json
android/app/src/release/google-services.json
*.jks                                      # Signing keys
*.keystore                                 # Signing keys
local.properties                           # Local SDK paths
```

**Backend**:
```
# NEVER commit these:
backend/api/.env                           # Environment variables
backend/api/.env.dev
backend/api/.env.prod
backend/api/service-account-*.json         # Firebase Admin SDK credentials
backend/api/**/*-credentials.json
```

**Root `.gitignore`** must include:
```gitignore
# Firebase
**/google-services.json
**/firebase-adminsdk-*.json
**/service-account*.json

# Environment variables
**/.env
**/.env.*
!**/.env.example

# API Keys
**/*credentials*.json
**/*secret*.json

# Android
*.jks
*.keystore
local.properties

# IDE
.idea/
.vscode/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db
```

**Safe to commit**:
```
✅ .env.example (with placeholder values)
✅ firebase.json (config, not credentials)
✅ firestore.rules
✅ database.rules.json
```

**Setup for new developers**:

1. Clone repo
2. Copy `.env.example` to `.env`
3. Get credentials from secure source (1Password, team lead, etc.)
4. Never share credentials via Slack/email
5. Download `google-services.json` from Firebase Console

**Emergency: If credentials leaked**:

1. **Immediately** rotate all API keys:
   - Firebase: regenerate in Firebase Console
   - Anthropic/OpenAI: regenerate in their dashboard
   - Render: update environment variables
2. Force-push to remove from Git history (if just committed)
3. Check Firebase usage for suspicious activity
4. Update all team members with new credentials

**Verification checklist before every commit**:
```bash
# Run this before git push:
git status

# Verify NO sensitive files are staged:
# ❌ google-services.json
# ❌ .env files
# ❌ *-credentials.json
# ❌ .jks/.keystore files
```

**Cursor-specific reminder**:
When Cursor generates Firebase setup code or environment configuration, it may suggest committing credential files. **Always verify** what's being committed and reject any suggestions to commit secrets.

---

### 11.5 Accessibility

**MVP Requirements**:
- Text contrast meets WCAG AA standards
- Touch targets minimum 48dp
- Screen reader support (contentDescription on UI elements)

**Nice to Have**:
- Dark mode
- Font size adjustments
- Voice input for messages

---

## 12. Out of Scope (Week 1)

The following features are explicitly **not required** for this 1-week sprint:

### 12.1 Communication Features
- ❌ Voice messages
- ❌ Video messages
- ❌ Voice/video calls
- ❌ Screen sharing
- ❌ File attachments (PDF, docs) - only images required
- ❌ Message reactions (emoji reactions)
- ❌ Message editing
- ❌ Message deletion
- ❌ Message forwarding
- ❌ Threads/replies to specific messages
- ❌ Pinned messages
- ❌ Message search within a chat (only cross-chat search)

### 12.2 Group Features
- ❌ Group admin roles
- ❌ Kick/ban members
- ❌ Group descriptions
- ❌ Group icons
- ❌ @channel or @here mentions
- ❌ Mute specific members

### 12.3 Advanced AI Features
- ❌ Voice-to-text transcription
- ❌ Sentiment analysis
- ❌ Automatic language translation (not required for Remote Team persona)
- ❌ AI chat bot (full conversational AI)
- ❌ Personalized AI training (learning from user behavior over time)
- ❌ Multi-modal AI (analyzing images)

### 12.4 User Experience
- ❌ Custom themes
- ❌ Chat backgrounds
- ❌ Message animations
- ❌ Stickers/GIFs
- ❌ User status messages ("In a meeting", "On vacation")
- ❌ Custom notification sounds
- ❌ Chat folders/organization

### 12.5 Security & Privacy
- ❌ End-to-end encryption
- ❌ Self-destructing messages
- ❌ Screenshot detection
- ❌ Two-factor authentication
- ❌ Login from multiple devices (only 1 device per user for MVP)

### 12.6 Integration
- ❌ Calendar integration (actual Google Calendar API)
- ❌ Email notifications
- ❌ Third-party integrations (Jira, GitHub, etc.)
- ❌ Webhooks
- ❌ Public API for external developers

### 12.7 Analytics & Admin
- ❌ Usage analytics dashboard
- ❌ Admin panel
- ❌ User reporting/blocking
- ❌ Content moderation tools
- ❌ Backup/export conversations

---

## 13. Success Metrics & Validation

### 13.1 MVP Success (24h)
**Binary Pass/Fail**:
- [ ] 2 devices can send real-time messages
- [ ] Messages persist after app restart
- [ ] Offline mode works (airplane mode test)
- [ ] Group chat with 3+ users functional
- [ ] Push notifications work (at least foreground)

**Qualitative**:
- Messages feel "instant" (no perceived lag)
- No crashes during basic usage
- UI is navigable (even if not polished)

---

### 13.2 AI Features Success (4 days)
**Quantitative**:
- Summarization accuracy: >85% (human eval of 10 test cases)
- Action item extraction: captures >90% of explicit commitments
- Priority detection precision: >80% (flagged messages are actually important)
- Search relevance: top 3 results contain answer >80% of time

**Qualitative**:
- AI responses feel helpful, not gimmicky
- Latency is acceptable (user doesn't give up waiting)
- No embarrassing hallucinations in demo scenarios

---

### 13.3 Final Demo Success (7 days)
**Demo Video Quality**:
- Tells compelling story (not just feature checklist)
- Shows realistic scenarios (not contrived examples)
- Demonstrates all 6 AI features clearly
- 5-7 minutes (concise, engaging)

**Technical Completeness**:
- Android APK installable on any device
- Backend API publicly accessible
- All features work end-to-end (no smoke and mirrors)

**Documentation Quality**:
- README allows someone to run the project in <30 minutes
- Architecture is clearly explained
- Persona Brainlift articulates design decisions

---

## 14. Risk Mitigation

### 14.1 High-Risk Items

**Risk: Firebase real-time doesn't work as expected**
- Mitigation: Test Firebase setup on Day 0 (before MVP deadline)
- Fallback: Use polling instead of listeners (less elegant, but functional)

**Risk: LLM API is too slow**
- Mitigation: Test API latency early, optimize prompts
- Fallback: Show loading indicator, make async (don't block UI)

**Risk: Proactive Assistant is annoying**
- Mitigation: Make it easy to dismiss, conservative trigger thresholds
- Fallback: Make it opt-in instead of automatic

**Risk: Push notifications don't work**
- Mitigation: Test FCM setup on Day 1
- Fallback: In-app notifications only for MVP (push can come later)

**Risk: Running out of time**
- Mitigation: Build in priority order (messaging → AI basics → proactive)
- Fallback: Cut Proactive Assistant, deliver 5 required AI features only

---

### 14.2 Time Management Strategy

**Day 0 (Today)**: 
- Setup monorepo structure
- Initialize Firebase project
- Test Firebase Auth + Firestore basic read/write
- Validate Android build works

**Day 1 (MVP Focus)**:
- Morning: One-on-one chat working end-to-end
- Afternoon: Group chat, presence, read receipts
- Evening: Push notifications, final MVP polish

**Day 2-3 (AI Features)**:
- Day 2: Summarization + Action Extraction + Priority Detection
- Day 3: Smart Search + Decision Tracking

**Day 4 (Early Submission)**:
- Morning: Bug fixes, AI polish
- Afternoon: Test all features, deploy backend
- Evening: Early submission (buffer for issues)

**Day 5-6 (Proactive Assistant)**:
- Day 5: Meeting time suggestion + scheduling detection
- Day 6: Proactive reminders, fine-tuning

**Day 7 (Final Polish)**:
- Morning: Demo video recording
- Afternoon: Documentation, Persona Brainlift
- Evening: Final submission

---

## 15. Appendix: Prompt Examples for Cursor

While implementation details are left to Cursor, here are example prompts to get started quickly:

### 15.1 Initial Setup
```
"Set up a Kotlin Android project with Jetpack Compose, targeting Android API 26+. 
Include Firebase Firestore, Realtime Database, Auth, and FCM dependencies. 
Use modern Android architecture (MVVM or MVI).

CRITICAL: Add google-services.json to .gitignore immediately. 
This file contains sensitive Firebase credentials and must NEVER be committed to Git.
Create .env.example files for documentation but never commit actual .env files."
```

### 15.2 Messaging Features
```
"Implement a chat screen with Firestore real-time listeners. 
The screen should display messages in a LazyColumn, ordered by timestamp. 
Include optimistic updates - messages appear immediately when sent, 
then update with server confirmation. Handle offline scenarios gracefully.
Use Firestore's built-in offline cache - do NOT use Room for messages."
```

### 15.3 AI Integration
```
"Create a Python FastAPI endpoint /ai/summarize that:
1. Accepts a chatId and optional 'since' timestamp
2. Fetches messages from Firestore using Firebase Admin SDK
3. Calls Claude API to generate a summary
4. Returns structured JSON with summary and key points
Include error handling and logging."
```

### 15.4 Proactive Assistant
```
"Implement a LangChain agent that monitors chat messages in real-time.
When it detects scheduling keywords ('let's schedule', 'meeting', 'sync'),
it should:
1. Extract participant names
2. Look up their time zones (mock data for MVP)
3. Suggest 3 meeting times
4. Return a formatted suggestion to display in the chat
Use function calling / tools for time zone lookup."
```

---

## 16. Glossary

**Optimistic Update**: Showing a change in the UI immediately, before server confirmation. Improves perceived performance.

**Presence**: Real-time status of whether a user is online, offline, or away.

**Read Receipt**: Indicator showing that a message has been seen by the recipient.

**RAG (Retrieval-Augmented Generation)**: AI technique where relevant context is retrieved from a database and provided to an LLM to improve response accuracy.

**LLM (Large Language Model)**: AI models like Claude or GPT-4 that generate text based on prompts.

**Function Calling / Tool Use**: LLM feature that allows the model to call predefined functions (e.g., search database, call API) to accomplish tasks.

**Firestore Listener**: Real-time subscription to database changes. When data updates, the listener callback is triggered.

**onDisconnect()**: Firebase Realtime Database feature that automatically executes a write operation when a client disconnects (e.g., set presence to offline).

**Cold Start**: Delay when a serverless function (Cloud Function) is invoked for the first time or after being idle.

**Latency**: Time delay between an action and its result (e.g., time between sending a message and it appearing on recipient's screen).

---

## 17. References

**Firebase Documentation**:
- Firestore: https://firebase.google.com/docs/firestore
- Realtime Database: https://firebase.google.com/docs/database
- Cloud Messaging: https://firebase.google.com/docs/cloud-messaging

**Android Development**:
- Jetpack Compose: https://developer.android.com/jetpack/compose
- Firebase Android SDK: https://firebase.google.com/docs/android/setup

**AI & Agents**:
- LangChain: https://python.langchain.com/docs/get_started/introduction
- Anthropic Claude: https://docs.anthropic.com/claude/docs
- OpenAI: https://platform.openai.com/docs

**Deployment**:
- Render: https://render.com/docs

---

## 18. Conclusion

Synapse combines the reliability of WhatsApp with the intelligence of modern AI to create a messaging app that truly helps remote teams work better. By focusing on the Remote Team Professional persona, we're solving real pain points: information overload, missed context, and coordination overhead.

The technical architecture leverages Firebase for world-class real-time infrastructure, while a separate Python API gives full control over complex AI features. The Proactive Assistant goes beyond reactive AI - it anticipates needs and suggests solutions before being asked.

Success means delivering an app where:
- Messages never get lost (rock-solid sync)
- Users find what they need instantly (smart search)
- Important stuff rises to the top (priority detection)
- The AI genuinely helps (not gimmicky features)

With one week, disciplined scope, and modern AI coding tools, this is absolutely achievable. Let's build something people would actually want to use every day.

---

**Product Name**: Synapse  
**Tagline**: "Where teams connect intelligently"  
**Document Version**: 1.0  
**Last Updated**: October 20, 2025  
**Author**: Synapse Team  
**Status**: Ready for Implementation