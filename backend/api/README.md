# Synapse AI Backend API

FastAPI backend for Remote Team Professional AI features.

## 🎯 Features

### 1. **Thread Summarization**
- Quickly catch up on long conversations
- Get key points without reading everything
- Perfect for async work across timezones

### 2. **Action Items Extraction**
- Automatic task detection from conversations
- Assignment tracking
- Deadline detection
- Priority classification

### 3. **Smart Semantic Search**
- Natural language queries
- Context-aware results
- Finds messages even with different wording

### 4. **Priority Detection**
- Automatically surface urgent messages
- Identify blocking issues
- Never miss critical deadlines

### 5. **Decision Tracking**
- Track what was decided
- See who agreed to what
- Reference decisions later

### 6. **Meeting Minutes Agent** (Advanced AI)
- Multi-step autonomous agent
- Generates comprehensive reports
- Executive summary + action items + decisions + next steps
- Professional formatted document

## 🚀 Quick Start

### 1. Install Dependencies

```bash
cd backend/api
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 2. Configure Environment

Create `.env` file:

```bash
# OpenAI API Key
OPENAI_API_KEY=sk-...your-key-here

# Firebase Admin SDK credentials path
FIREBASE_CREDENTIALS_PATH=./firebase-credentials.json

# Environment
ENVIRONMENT=development
```

### 3. Add Firebase Credentials

Download Firebase Admin SDK private key from Firebase Console:
- Go to Project Settings → Service Accounts
- Click "Generate new private key"
- Save as `firebase-credentials.json` in this directory

### 4. Run Server

```bash
uvicorn main:app --reload --port 8000
```

API will be available at: `http://localhost:8000`
Interactive docs: `http://localhost:8000/docs`

## 📡 API Endpoints

### Thread Summarization
```http
POST /api/summarize
```

**Request:**
```json
{
  "conversation_id": "conv123",
  "start_date": "2024-01-01T00:00:00",
  "end_date": "2024-01-31T23:59:59",
  "max_messages": 100
}
```

**Response:**
```json
{
  "conversation_id": "conv123",
  "summary": "Team discussed project deadline and assigned tasks...",
  "key_points": [
    "Deadline extended to Feb 15",
    "John will handle backend",
    "Sarah reviewing designs"
  ],
  "participant_count": 3,
  "message_count": 45,
  "date_range": "2024-01-15 to 2024-01-20",
  "processing_time_ms": 1250
}
```

### Action Items Extraction
```http
POST /api/action-items
```

### Smart Search
```http
POST /api/search
```

### Priority Detection
```http
POST /api/priority
```

### Decision Tracking
```http
POST /api/decisions
```

### Meeting Minutes Agent
```http
POST /api/meeting-minutes
```

## 🏗️ Architecture

```
backend/api/
├── main.py              # FastAPI app entrypoint
├── models/
│   └── schemas.py       # Pydantic request/response models
├── services/
│   ├── firebase_service.py   # Firestore data fetching
│   ├── openai_service.py     # OpenAI API integration
│   └── agent_service.py      # Multi-step AI agent
└── routers/
    ├── summarization.py      # Thread summaries
    ├── action_items.py       # Task extraction
    ├── search.py             # Semantic search
    ├── priority.py           # Urgency detection
    ├── decisions.py          # Decision tracking
    └── agent.py              # Meeting minutes
```

## 🔐 Authentication

Currently using mock auth. To enable Firebase Auth:

1. Uncomment auth dependency in routers
2. Android app sends Firebase ID token in header:
   ```
   Authorization: Bearer <firebase_id_token>
   ```

## 🚢 Deployment (Render)

### Option 1: Using render.yaml

```yaml
services:
  - type: web
    name: synapse-ai-api
    runtime: python
    buildCommand: pip install -r requirements.txt
    startCommand: uvicorn main:app --host 0.0.0.0 --port $PORT
    envVars:
      - key: OPENAI_API_KEY
        sync: false
      - key: FIREBASE_CREDENTIALS_PATH
        value: ./firebase-credentials.json
```

### Option 2: Manual Setup

1. Create new Web Service on Render
2. Connect GitHub repo
3. Set root directory: `backend/api`
4. Build command: `pip install -r requirements.txt`
5. Start command: `uvicorn main:app --host 0.0.0.0 --port $PORT`
6. Add environment variables
7. Upload `firebase-credentials.json` as secret file

## 🧪 Testing

```bash
# Test health endpoint
curl http://localhost:8000/health

# Test summarization (replace with real conversation_id)
curl -X POST http://localhost:8000/api/summarize \
  -H "Content-Type: application/json" \
  -d '{
    "conversation_id": "your-conv-id",
    "max_messages": 50
  }'
```

## 📊 Performance Targets

- Thread Summarization: < 2s
- Action Items: < 3s
- Smart Search: < 2s
- Priority Detection: < 2s
- Decision Tracking: < 3s
- Meeting Minutes Agent: < 15s (multi-step)

## 🔧 Development

### Add New Feature

1. Create schema in `models/schemas.py`
2. Add service function in `services/openai_service.py`
3. Create router in `routers/your_feature.py`
4. Register router in `main.py`

### Debug Mode

```bash
# Enable debug logging
PYTHONPATH=. python -c "
from services import openai_service
messages = [...]  # Your test messages
result = await openai_service.summarize_thread(messages)
print(result)
"
```

## 📝 License

MIT

