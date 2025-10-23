# Synapse - Final Project Plan

**Project:** MessageAI (Synapse)  
**Target Grade:** A (90-100 points)  
**Current Estimated Score:** 71-76/100 (including +1 bonus for dark mode)  
**Gap to Close:** 19-24 points  
**Deadline:** [Your Deadline Here]

---

## Executive Summary

Synapse is a real-time messaging app with AI capabilities built for the MessageAI Gauntlet project. The core messaging infrastructure is **solid** (excellent real-time delivery, offline support, mobile lifecycle handling, and **dark mode support**). The primary gap is the **complete absence of AI features** (0/30 points) and **missing required deliverables** (-30 points penalty).

**Path to A Grade:**
1. Implement 5 required AI features + 1 advanced capability → +30 pts
2. Complete all deliverables (video, brainlift, social post) → +30 pts (remove penalty)
3. Polish group chat UI → +4-6 pts
4. Add innovation features → +3 pts potential

---

## Current Status Assessment

### ✅ **What's Working Well (71-76/100 baseline)**

#### **Core Messaging Infrastructure (33-35/35 pts)**
- ✅ **Real-Time Delivery (11-12/12):** Firebase snapshot listeners, sub-200ms latency, typing indicators, presence updates
- ✅ **Offline Support (11-12/12):** Firestore persistence enabled, automatic retry, message status UI (⏱️/✓/✓✓), connection indicators
- ⚠️ **Group Chat (5-7/11):** Backend works, messaging works, but missing member list UI and detailed read receipts

#### **Mobile App Quality (16-18/20 pts)**
- ✅ **Lifecycle Handling (7-8/8):** Background/foreground transitions, push notifications, deep links working
- ✅ **Performance & UX (9-10/12):** Fast launch, smooth scrolling, optimistic UI, keyboard handling, WhatsApp-style animations, **dark mode support**, Material 3 dynamic colors

#### **Technical Implementation (9/10 pts)**
- ✅ **Architecture (4/5):** Clean layers, Hilt DI, Repository pattern, secure Firebase config
- ✅ **Auth & Data (5/5):** Firebase Auth, user profiles, Firestore with cache, session handling

#### **Documentation (4/5 pts)**
- ✅ **Setup Docs (3/3):** Comprehensive README, step-by-step setup, architecture docs
- ⚠️ **Deployment (1/2):** Runs locally, not yet on TestFlight/Play Store

#### **Bonus Points (+6/10 potential)**
- ✅ **Polish (+3):** Dark mode with Material 3 dynamic colors ✅, WhatsApp-style animations ✅, professional layout ✅
- ⚠️ **Advanced Features (+2 potential):** Message reactions (to implement), voice messages (potential), link unfurling (potential)
- ⚠️ **Innovation (+1 potential):** AI features will provide this
- ⚠️ **Technical Excellence (+2 potential):** Can be achieved with exceptional AI implementation
- ⚠️ **Additional Polish (+2 potential):** Accessibility features, comprehensive test coverage

---

### 🚨 **Critical Gaps (30 points missing + 30 penalty)**

#### **AI Features - ZERO IMPLEMENTATION (0/30 pts)**
- ❌ No persona selected
- ❌ No LLM integration (OpenAI/Claude/Gemini)
- ❌ No RAG pipeline
- ❌ No required AI features (need 5)
- ❌ No advanced AI capability (need 1)

#### **Required Deliverables - NOT SUBMITTED (-30 pts penalty)**
- ❌ Demo video (5-7 minutes)
- ❌ Persona brainlift (1 page)
- ❌ Social post (X/LinkedIn)

#### **Group Chat Polish (4-6 pts missing)**
- ❌ Member list screen
- ❌ Read receipts with names ("João read", "Maria read")
- ❌ Typing indicators with names ("João is typing...")

---

## Final Implementation Plan

### **Phase 1: AI Foundation (Week 1) - 15 points**

**Goal:** Setup AI infrastructure and implement first 3 features

#### **Task 1.1: Persona Selection & Setup (Day 1)**
- [ ] Choose persona: **Remote Team Professional** (recommended)
- [ ] Document pain points in brainlift doc
- [ ] Setup OpenAI API (GPT-4) or Claude (Sonnet 3.5)
- [ ] Create backend cloud functions for AI calls
- [ ] Secure API keys in Firebase Functions

**Deliverables:**
- `docs/Persona Brainlift.md` (draft)
- `functions/src/ai/openai-client.ts` or equivalent
- Environment variables configured

---

#### **Task 1.2: RAG Pipeline Implementation (Day 2-3)**
- [ ] Create message vectorization system
- [ ] Store embeddings in Firestore or Pinecone
- [ ] Implement context retrieval (last 10-20 messages)
- [ ] Test RAG quality with sample conversations

**Deliverables:**
- `functions/src/ai/rag.ts`
- Vector database setup
- RAG endpoint working

---

#### **Task 1.3: AI Feature 1 - Smart Reply Suggestions (Day 4)**
**Pain Point:** Remote workers need to respond quickly to multiple threads

- [ ] Backend: Generate 3 contextual reply suggestions
- [ ] Frontend: Show suggestions above keyboard
- [ ] Tap suggestion → insert into message box
- [ ] Analytics: Track suggestion acceptance rate

**Technical:**
```kotlin
// Android UI
@Composable
fun SmartReplySuggestions(suggestions: List<String>, onSelect: (String) -> Unit) {
    LazyRow {
        items(suggestions) { suggestion ->
            SuggestionChip(text = suggestion, onClick = { onSelect(suggestion) })
        }
    }
}
```

**Success Criteria:**
- 80%+ relevant suggestions
- <2s response time
- Clean UI integration

---

#### **Task 1.4: AI Feature 2 - Thread Summarization (Day 5)**
**Pain Point:** Catching up on long conversations takes too much time

- [ ] Backend: Summarize last N messages (N=10-50)
- [ ] Frontend: "Summarize" button in conversation menu
- [ ] Display summary in modal/bottom sheet
- [ ] Show key points in bullet format

**Technical:**
```kotlin
// Prompt example
val prompt = """
Summarize this conversation in 3-5 bullet points:
- Focus on decisions made
- Include action items
- Note any blockers mentioned

Messages:
${messages.joinToString("\n") { "${it.sender}: ${it.text}" }}
"""
```

**Success Criteria:**
- Captures main points accurately
- <3s response time
- Easy to read format

---

#### **Task 1.5: AI Feature 3 - Action Items Extraction (Day 6)**
**Pain Point:** Action items get lost in conversation threads

- [ ] Backend: Extract todos/commitments from messages
- [ ] Frontend: Show action items list in conversation
- [ ] Mark action items as complete
- [ ] Highlight messages containing action items

**Technical:**
```kotlin
data class ActionItem(
    val description: String,
    val assignedTo: String?,
    val dueDate: String?,
    val messageId: String,
    val isCompleted: Boolean = false
)
```

**Success Criteria:**
- 85%+ accurate extraction
- Shows who committed to what
- Links back to original message

---

### **Phase 2: Advanced AI + Remaining Features (Week 2) - 15 points**

#### **Task 2.1: AI Feature 4 - Priority Detection (Day 1)**
**Pain Point:** Important messages get buried in busy channels

- [ ] Backend: Classify messages by urgency (High/Medium/Low)
- [ ] Frontend: Priority badge on messages
- [ ] Filter view: "Show only High priority"
- [ ] Push notification priority levels

**Technical:**
```kotlin
enum class MessagePriority {
    HIGH,    // "URGENT", "ASAP", deadlines, @mentions
    MEDIUM,  // Questions, requests
    LOW      // General chat, FYI
}
```

**Success Criteria:**
- 80%+ accurate priority detection
- Clear visual indicators
- Useful filtering

---

#### **Task 2.2: AI Feature 5 - Smart Search (Day 2)**
**Pain Point:** Hard to find specific information in old conversations

- [ ] Backend: Semantic search using embeddings
- [ ] Frontend: Search bar with AI-powered results
- [ ] Show context around matches
- [ ] Group results by conversation

**Technical:**
```kotlin
// Search with natural language
searchRepository.semanticSearch(
    query = "When is the deployment scheduled?",
    conversationId = currentConvId
)
```

**Success Criteria:**
- Finds relevant messages even with different wording
- <2s search response
- Better than keyword search

---

#### **Task 2.3: Advanced Capability - Multi-Step Agent (Day 3-5)**
**Choice:** Multi-Step Agent using LangGraph or CrewAI

**Use Case:** "Prepare for Meeting" Agent
1. Summarize relevant conversations
2. Extract action items assigned to user
3. List unresolved questions
4. Draft meeting agenda
5. Send summary to user

**Technical:**
```typescript
// functions/src/agents/meeting-prep-agent.ts
import { StateGraph } from "langgraph";

const graph = new StateGraph()
  .addNode("summarize", summarizeConversations)
  .addNode("extractActions", extractActionItems)
  .addNode("findQuestions", findOpenQuestions)
  .addNode("draftAgenda", createAgenda)
  .addEdge("summarize", "extractActions")
  .addEdge("extractActions", "findQuestions")
  .addEdge("findQuestions", "draftAgenda");
```

**Success Criteria:**
- Executes 5+ step workflow
- Handles errors gracefully
- <15s total execution time
- Useful output for real meetings

---

### **Phase 3: Polish & Deliverables (Week 3) - 10 points + remove 30 penalty**

#### **Task 3.1: Group Chat UI Enhancements (Day 1-2)**
- [ ] **Member List Screen:**
  - Show all group members with avatars
  - Online status indicators
  - Admin badge for creator
  - Option to add/remove members (if admin)

- [ ] **Detailed Read Receipts:**
  - Long-press message → "Info" option
  - Show list: "Read by João (2m ago), Maria (5m ago)"
  - Show "Delivered to Pedro" for unread

- [ ] **Typing Indicators with Names:**
  - Single user: "João is typing..."
  - Multiple users: "João and Maria are typing..."
  - Max 2 names, then "João and 2 others are typing..."

**Deliverables:**
```kotlin
// GroupMembersScreen.kt
@Composable
fun GroupMembersScreen(members: List<User>, onlineMemberIds: Set<String>)

// MessageInfoSheet.kt
@Composable
fun MessageInfoBottomSheet(readBy: List<ReadReceipt>, deliveredTo: List<String>)
```

---

#### **Task 3.2: Demo Video Production (Day 3-4)**
**Duration:** 5-7 minutes  
**Equipment:** 2 physical Android devices

**Script Outline:**

1. **Intro (30s)**
   - "Hi, I'm [Name], and this is Synapse - an AI-powered messaging app for Remote Teams"
   - Show app on two devices side-by-side

2. **Real-Time Messaging (1m)**
   - Send messages between devices
   - Show instant delivery
   - Demonstrate typing indicators
   - Show presence updates (online/offline)

3. **Group Chat (1m)**
   - Create group with 3+ participants
   - Send messages from multiple devices
   - Show member list
   - Demonstrate read receipts

4. **Offline Support (1m)**
   - Turn on airplane mode on Device A
   - Send 3 messages while offline (show ⏱️ pending icon)
   - Turn on airplane mode on Device B
   - Turn off airplane mode on Device A → messages deliver
   - Device B comes online → receives all missed messages

5. **App Lifecycle (30s)**
   - Background app → send message → notification appears
   - Tap notification → deep link to conversation
   - Force quit app → reopen → conversation intact

6. **AI Features Demo (2m)**
   - **Smart Reply:** Show 3 suggestions, tap one
   - **Thread Summary:** Tap "Summarize" button, show result
   - **Action Items:** Show extracted todos from conversation
   - **Priority Detection:** Show high-priority message badge
   - **Smart Search:** Search "when is deployment" → finds relevant messages

7. **Advanced Agent Demo (1m)**
   - Run "Prepare for Meeting" agent
   - Show step-by-step execution
   - Display final meeting prep report

8. **Technical Overview (30s)**
   - Architecture diagram (show on screen)
   - "Built with Kotlin + Jetpack Compose + Firebase + OpenAI"
   - "RAG pipeline for context-aware AI"
   - "LangGraph multi-step agent"

9. **Outro (30s)**
   - "Synapse helps remote teams stay organized with AI-powered features"
   - Show GitHub repo URL
   - "Thanks for watching!"

**Deliverables:**
- `Synapse Demo.mp4` (uploaded to YouTube/Vimeo)
- Link in README

---

#### **Task 3.3: Persona Brainlift Document (Day 4)**
**Length:** 1 page

**Structure:**

```markdown
# Synapse - Persona Brainlift

## Chosen Persona: Remote Team Professional

**Who:** Software engineers, product managers, designers working in distributed teams

**Pain Points:**
1. **Information Overload:** 100+ messages per day across multiple channels
2. **Context Switching:** Hard to catch up after being away from conversation
3. **Lost Action Items:** Commitments get buried in chat history
4. **Search Frustration:** Can't find that important decision from last week
5. **Meeting Prep Time:** Manually reviewing conversations before sync meetings

## AI Features → Pain Point Mapping

| AI Feature | Solves Pain Point | How It Helps |
|------------|------------------|--------------|
| Smart Reply | Information Overload | Quick responses without typing, 50% faster replies |
| Thread Summary | Context Switching | 5-minute catch-up becomes 30 seconds |
| Action Items | Lost Commitments | Automatic todo tracking, never miss a deadline |
| Priority Detection | Information Overload | Focus on urgent messages first, ignore noise |
| Smart Search | Search Frustration | Find "that decision" in seconds with natural language |

## Advanced AI: Meeting Prep Agent

**Problem:** Engineers spend 15-30 minutes before each meeting reviewing Slack/email

**Solution:** Multi-step agent that:
1. Summarizes relevant conversations
2. Extracts your action items
3. Lists unresolved questions
4. Drafts meeting agenda

**Impact:** Reduces prep time from 20 minutes to 2 minutes (10x improvement)

## Key Technical Decisions

1. **RAG Pipeline:** Vectorize last 20 messages for context-aware AI
2. **Firebase + OpenAI:** Real-time messaging + powerful language model
3. **LangGraph:** Multi-step agent with proper error handling
4. **Mobile-First:** Native Android for best performance
```

**Deliverables:**
- `docs/Persona Brainlift.md`

---

#### **Task 3.4: Social Media Post (Day 5)**
**Platforms:** X (Twitter) and LinkedIn

**Content:**

```
🚀 Just built Synapse - an AI-powered messaging app for remote teams!

Built for the @GauntletAI MessageAI project, Synapse uses:
• Real-time messaging with Firebase
• AI-powered features (smart replies, summaries, action tracking)
• Multi-step LangGraph agent for meeting prep
• Native Android with Jetpack Compose

Key features:
✅ Smart Reply Suggestions (80%+ accuracy)
✅ Thread Summarization (5-min catch-up → 30 sec)
✅ Action Item Extraction (never miss a commitment)
✅ Priority Detection (focus on what matters)
✅ Semantic Search (find anything instantly)

[Demo Video] 🎥
[GitHub Repo] 💻

Built with: Kotlin, Compose, Firebase, OpenAI GPT-4, LangGraph

#AI #Messaging #AndroidDev #Firebase #OpenAI #RemoteWork
```

**Deliverables:**
- Post on X and LinkedIn
- Tag @GauntletAI
- Include video + screenshots
- Link to GitHub

---

## Success Metrics

### **Target Score Breakdown**

| Section | Target | Strategy |
|---------|--------|----------|
| Real-Time Delivery | 12/12 | Already excellent, maintain |
| Offline Support | 12/12 | Already excellent, maintain |
| Group Chat | 10/11 | Add member list + detailed read receipts |
| Mobile Lifecycle | 8/8 | Already excellent, maintain |
| Performance & UX | 11/12 | Already good, minor polish |
| **AI Features** | **14/15** | Implement all 5 features with 80%+ quality |
| **Persona Fit** | **5/5** | Clear pain point mapping |
| **Advanced AI** | **9/10** | Multi-step agent with LangGraph |
| Architecture | 5/5 | Add RAG pipeline |
| Auth & Data | 5/5 | Already excellent, maintain |
| Docs | 3/3 | Already excellent, maintain |
| Deployment | 2/2 | Deploy to TestFlight or APK |
| **Deliverables** | **0** | Complete all (remove -30 penalty) |
| Bonus | +6 | Polish (+3 - dark mode ✅, animations ✅), Advanced features (+2), Innovation (+1 potential) |
| **TOTAL** | **97-101/100** | **A Grade** |

---

## Risk Management

### **High Risks**

1. **AI Feature Quality:**
   - **Risk:** Features work but accuracy <80%
   - **Mitigation:** Test with real conversations, iterate prompts, use GPT-4 (not 3.5)

2. **Time Constraint:**
   - **Risk:** Not enough time to implement all features
   - **Mitigation:** Prioritize core features first, cut scope if needed (5 features > 4 excellent features)

3. **LangGraph Complexity:**
   - **Risk:** Multi-step agent too complex to implement
   - **Mitigation:** Start simple (3 steps), expand if time allows, fallback to simpler "Proactive Assistant" if blocked

### **Medium Risks**

4. **API Costs:**
   - **Risk:** OpenAI usage exceeds budget
   - **Mitigation:** Cache responses, use GPT-3.5-turbo for simple tasks, rate limit users

5. **Demo Video Quality:**
   - **Risk:** Poor audio/video quality
   - **Mitigation:** Use good microphone, record in quiet room, test recording setup first

---

## Daily Checklist Template

```markdown
## Day [X] - [Date]

**Goal:** [Task from plan]

**Morning (3h):**
- [ ] Task 1
- [ ] Task 2
- [ ] Task 3

**Afternoon (3h):**
- [ ] Task 4
- [ ] Task 5
- [ ] Test & debug

**EOD Status:**
- ✅ Completed: [list]
- 🚧 In Progress: [list]
- ⚠️ Blocked: [list + blocker]

**Tomorrow's Priority:** [Next task]
```

---

## Resources & References

### **AI/LLM Integration**
- OpenAI API Docs: https://platform.openai.com/docs
- LangGraph Tutorial: https://langchain-ai.github.io/langgraph/
- RAG Best Practices: https://www.pinecone.io/learn/retrieval-augmented-generation/

### **Android/Firebase**
- Firebase Functions: https://firebase.google.com/docs/functions
- Jetpack Compose: https://developer.android.com/jetpack/compose
- Firestore Offline: https://firebase.google.com/docs/firestore/manage-data/enable-offline

### **Demo Video Tips**
- Screen Recording: Use Android Studio Device Manager + ADB screenrecord
- Video Editing: DaVinci Resolve (free) or iMovie
- Thumbnail: Canva templates

---

## Final Notes

**Current Strengths:**
- Excellent core messaging infrastructure (real-time, offline support)
- Solid mobile app quality (dark mode ✅, animations, performance)
- Good technical architecture (clean layers, DI, Firebase)

**Focus Areas:**
- AI features (30 points available)
- Deliverables (30 points penalty to remove)
- Group chat polish (4-6 points)

**Path to A:** Implement all AI features with high quality, complete deliverables, polish group chat UI.

**Estimated Effort:** 15-20 days of focused work

**Expected Final Score:** 97-101/100 (A+)

---

**Last Updated:** [Today's Date]  
**Document Owner:** [Your Name]  
**Status:** Ready for Implementation

