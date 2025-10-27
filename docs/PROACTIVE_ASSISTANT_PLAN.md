# Proactive Assistant - Implementation Plan

## 🎯 Goal
Implement a multi-agent LangGraph system that proactively suggests helpful information based on conversation context.

---

## 🏗️ Architecture Overview

```
User sends message → Android triggers /proactive (async) → Python LangGraph workflow

1. Context Detector Agent (Gatekeeper)
   ↓ (should_act = false) → END
   ↓ (should_act = true)
2. Router (conditional edges)
   ↓
3. Specialized Agent (Cinema/Restaurant/Generic)
   ↓
4. Create AI message in Firestore
   ↓
5. Android receives via existing listener
```

---

## 📋 Implementation Checklist

### **PHASE 1: Backend (Python) - ~2h**

#### ✅ Already Done:
- [x] Created `proactive_service.py` with LangGraph workflow
- [x] Context Detector Agent (anti-spam + LLM analysis)
- [x] 3 Specialized Agents (Cinema, Restaurant, Generic)
- [x] StateGraph with conditional routing

#### 🔲 TODO:

**1.1. Create Router (`routers/proactive.py`)** - 30min
```python
# File: backend/api/routers/proactive.py

from fastapi import APIRouter, Depends
from models.schemas import ProactiveRequest, ProactiveResponse
from services import firebase_service, proactive_service

router = APIRouter()

@router.post("/proactive", response_model=ProactiveResponse)
async def trigger_proactive_assistant(
    request: ProactiveRequest,
    user_id: str = Depends(lambda: "mock_user")
):
    """
    Proactive Assistant - Multi-Agent System
    
    Analyzes conversation context and proactively suggests:
    - Movies/entertainment (cinema)
    - Restaurants/dining (food)
    - Generic helpful suggestions
    """
    try:
        # Fetch recent messages
        messages = await firebase_service.get_conversation_messages(
            conversation_id=request.conversation_id,
            max_messages=20  # Last 20 messages for context
        )
        
        if not messages:
            return ProactiveResponse(
                success=True,
                should_act=False,
                reason="no_messages"
            )
        
        # Run proactive workflow
        result = await proactive_service.run_proactive_assistant(
            messages=messages,
            conversation_id=request.conversation_id
        )
        
        # If should act, create AI message
        if result['should_act'] and result['suggestion_text']:
            participants = await firebase_service.get_conversation_participants(
                request.conversation_id
            )
            member_ids = [p['id'] for p in participants]
            
            message_id = await firebase_service.create_ai_message(
                conversation_id=request.conversation_id,
                text=result['suggestion_text'],
                message_type="ai_summary",  # Use existing type
                member_ids=member_ids,
                send_notification=False,
                metadata={
                    "feature": "proactive_assistant",
                    "context_type": result['context_type'],
                    "confidence": result['confidence'],
                    "aiGenerated": True
                }
            )
            
            return ProactiveResponse(
                success=True,
                should_act=True,
                context_type=result['context_type'],
                confidence=result['confidence'],
                message_id=message_id
            )
        else:
            return ProactiveResponse(
                success=True,
                should_act=False,
                reason=result['reason']
            )
    
    except Exception as e:
        return ProactiveResponse(
            success=False,
            should_act=False,
            reason=f"error: {str(e)}"
        )
```

**1.2. Add Schemas (`models/schemas.py`)** - 15min
```python
# Add to models/schemas.py

class ProactiveRequest(BaseModel):
    conversation_id: str

class ProactiveResponse(BaseModel):
    success: bool
    should_act: bool
    context_type: Optional[str] = None
    confidence: Optional[float] = None
    message_id: Optional[str] = None
    reason: Optional[str] = None
```

**1.3. Register Router (`main.py`)** - 5min
```python
# In backend/api/main.py

from routers import proactive

app.include_router(proactive.router, prefix="/ai", tags=["AI - Proactive"])
```

**1.4. Test Locally** - 15min
```bash
cd backend/api
python main.py

# Test with curl:
curl -X POST http://localhost:8000/ai/proactive \
  -H "Content-Type: application/json" \
  -d '{"conversation_id": "test123"}'
```

---

### **PHASE 2: Android - ~1.5h**

**2.1. Add Preferences (`data/local/UserPreferences.kt`)** - 10min
```kotlin
data class UserPreferences(
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val forceAIError: Boolean = false,
    val devSummary: Boolean = false,
    val proactiveAssistantEnabled: Boolean = false  // NEW
)
```

**2.2. Settings UI (`ui/settings/SettingsScreen.kt`)** - 30min
```kotlin
// Add to Settings Screen after "Dev Summary" section

// Proactive Assistant Section
Text(
    text = "AI Features",
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(top = 16.dp)
)

SettingsItem(
    title = "Proactive Assistant",
    description = "AI will analyze conversations and suggest helpful information automatically",
    checked = uiState.preferences.proactiveAssistantEnabled,
    onCheckedChange = { viewModel.updateProactiveAssistant(it) }
)
```

**2.3. ViewModel Update (`ui/settings/SettingsViewModel.kt`)** - 5min
```kotlin
fun updateProactiveAssistant(enabled: Boolean) {
    viewModelScope.launch {
        settingsRepository.updatePreferences(
            uiState.value.preferences.copy(proactiveAssistantEnabled = enabled)
        )
    }
}
```

**2.4. API Interface (`data/remote/SynapseAIApi.kt`)** - 10min
```kotlin
@POST("ai/proactive")
suspend fun triggerProactive(
    @Body request: ProactiveRequest
): ProactiveResponse

data class ProactiveRequest(
    @SerializedName("conversation_id") val conversationId: String
)

data class ProactiveResponse(
    val success: Boolean,
    @SerializedName("should_act") val shouldAct: Boolean,
    @SerializedName("context_type") val contextType: String?,
    val confidence: Float?,
    @SerializedName("message_id") val messageId: String?,
    val reason: String?
)
```

**2.5. AIRepository Method (`data/repository/AIRepository.kt`)** - 20min
```kotlin
/**
 * Trigger proactive assistant (async, non-blocking)
 * Uses job tracking to prevent duplicate suggestions
 */
fun triggerProactiveAsync(conversationId: String) {
    // Check if job already running for this conversation
    val existingJob = _activeJobs.value.keys
        .firstOrNull { it.startsWith("proactive_$conversationId") }
    
    if (existingJob != null) {
        Log.d(TAG, "⏳ [proactive_$conversationId] Job already running, skipping")
        return
    }
    
    // Create job ID
    val jobId = "proactive_${conversationId}_${System.currentTimeMillis()}"
    
    // Track job
    _activeJobs.value += (jobId to "RUNNING")
    _jobCount.value += 1
    
    Log.d(TAG, "🚀 [${jobId}] Proactive assistant triggered (async)")
    
    // Launch in application scope (survives ViewModel)
    applicationScope.launch {
        try {
            val response = api.triggerProactive(
                ProactiveRequest(conversationId = conversationId)
            )
            
            if (response.shouldAct) {
                Log.d(TAG, "✅ [${jobId}] Suggestion sent: ${response.contextType}")
            } else {
                Log.d(TAG, "⏸️  [${jobId}] No action needed: ${response.reason}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [${jobId}] Proactive failed: ${e.message}")
        } finally {
            // Remove job tracking
            _activeJobs.value -= jobId
            _jobCount.value -= 1
        }
    }
}
```

**2.6. Trigger on Send (`ui/conversation/ConversationViewModel.kt`)** - 15min
```kotlin
fun sendMessage(text: String) {
    if (text.isBlank()) return
    
    val currentConvId = _conversationId.value ?: return
    
    viewModelScope.launch {
        try {
            // 1. Send message to Firestore (existing flow)
            messageRepository.sendMessage(
                conversationId = currentConvId,
                text = text.trim()
            )
            
            // 2. Trigger proactive assistant IF enabled (async, non-blocking)
            if (settingsRepository.isProactiveAssistantEnabled()) {
                aiRepository.triggerProactiveAsync(currentConvId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
        }
    }
}
```

**2.7. Settings Repository Helper** - 5min
```kotlin
// In SettingsRepository
suspend fun isProactiveAssistantEnabled(): Boolean {
    return getPreferences().first().proactiveAssistantEnabled
}
```

---

### **PHASE 3: Testing - ~30min**

**3.1. Create Test Conversation** - 10min
1. Open Android app
2. Create group with 2-3 users
3. Send test messages:
   - "Vamos ao cinema sábado?"
   - "Boa ideia! Qual filme?"
   - "Alguém tem sugestão?"

**3.2. Enable Proactive** - 5min
1. Go to Settings
2. Toggle "Proactive Assistant" ON
3. Go back to conversation

**3.3. Test Suggestions** - 10min
1. Send: "Vamos assistir um filme"
2. Wait ~10s
3. Should see AI suggestion with movies

**3.4. Test Anti-Spam** - 5min
1. Send 3 messages quickly
2. Should only get 1 AI response
3. AI should NOT respond again until 10+ messages later

**3.5. Test Restaurant Context** - 5min
1. Send: "Onde vamos jantar?"
2. Wait ~10s
3. Should see restaurant suggestions

---

### **PHASE 4: Deploy & Document - ~30min**

**4.1. Commit Backend** - 10min
```bash
git add backend/api/services/proactive_service.py
git add backend/api/routers/proactive.py
git add backend/api/models/schemas.py
git add backend/api/main.py
git commit -m "feat: implement Proactive Assistant (LangGraph multi-agent)"
```

**4.2. Commit Android** - 10min
```bash
git add android/app/src/main/java/com/synapse/data/repository/AIRepository.kt
git add android/app/src/main/java/com/synapse/data/remote/SynapseAIApi.kt
git add android/app/src/main/java/com/synapse/ui/conversation/ConversationViewModel.kt
git add android/app/src/main/java/com/synapse/ui/settings/*.kt
git commit -m "feat: integrate Proactive Assistant in Android"
```

**4.3. Push to Remote** - 5min
```bash
git push origin main
```

**4.4. Update README** - 5min
Add Proactive Assistant to AI Features section

---

## ⏱️ Time Estimates

| Phase | Task | Time |
|-------|------|------|
| 1 | Backend Setup | 1h 5min |
| 2 | Android Integration | 1h 35min |
| 3 | Testing | 30min |
| 4 | Deploy & Docs | 30min |
| **TOTAL** | | **~3h 40min** |

---

## 🎯 Success Criteria

- [ ] Context Detector correctly identifies cinema/restaurant/generic contexts
- [ ] Anti-spam prevents duplicate suggestions (max 1 per 10 messages)
- [ ] Suggestions appear within 10 seconds of trigger
- [ ] Settings toggle works (enable/disable proactive)
- [ ] Job tracking prevents concurrent API calls
- [ ] LangGraph workflow executes all steps correctly
- [ ] AI messages render correctly in chat

---

## 🏆 Rubric Alignment

**Advanced AI Capability (10 points) - Expected: 9-10**

✅ **Multi-Step Agent**: 
- Context Detector → Router → Specialist (3 steps)
- StateGraph orchestration with conditional edges

✅ **Proactive Assistant**:
- Monitors conversations intelligently (last 20 messages)
- Triggers at right moments (anti-spam + activity check)
- Context-aware routing (cinema vs restaurant vs generic)

✅ **Uses Framework Correctly**:
- LangGraph StateGraph
- Conditional edges for routing
- Proper state management

✅ **Response Times**:
- Target: <15s for agents
- Expected: ~8-10s (acceptable)

✅ **Seamless Integration**:
- Uses existing Firestore listener
- Async/non-blocking
- Opt-in via Settings

---

## 🚀 Next Steps

1. Start with Phase 1 (Backend)
2. Test locally with curl
3. Move to Phase 2 (Android)
4. E2E testing
5. Commit & push
6. Record demo video

**Ready to start? Let's go!** 🎯

