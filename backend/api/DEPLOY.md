# 🚀 Deploy Synapse AI API to Render

## Prerequisites
- GitHub repository with the code pushed
- OpenAI API Key
- Firebase Admin SDK credentials JSON file

## Step-by-Step Deployment

### 1. Create New Web Service on Render

1. Go to https://dashboard.render.com/
2. Click **"New +"** → **"Web Service"**
3. Connect your GitHub repository: `davicaetano/synapse`
4. Configure:
   - **Name**: `synapse-ai-api`
   - **Region**: Oregon (or closest to you)
   - **Branch**: `main`
   - **Root Directory**: `backend/api`
   - **Runtime**: `Python 3`
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `uvicorn main:app --host 0.0.0.0 --port $PORT`
   - **Plan**: Free

### 2. Set Environment Variables

In Render dashboard, go to **Environment** tab and add:

#### Required Variables:

1. **OPENAI_API_KEY**
   - Value: `sk-proj-...` (your OpenAI API key)
   - Click **"Add Environment Variable"**

2. **FIREBASE_CREDENTIALS_PATH**
   - Value: `./firebase-credentials.json`

3. **ENVIRONMENT**
   - Value: `production`

### 3. Upload Firebase Credentials as Secret File

#### Option A: Using Render Secret Files (Recommended)
1. Go to **Secret Files** tab in Render
2. Click **"Add Secret File"**
3. **Filename**: `firebase-credentials.json`
4. **Contents**: Copy and paste your entire Firebase credentials JSON:
   ```json
   {
     "type": "service_account",
     "project_id": "synapse-dev-d6920",
     "private_key_id": "...",
     "private_key": "-----BEGIN PRIVATE KEY-----\n...",
     "client_email": "...",
     "client_id": "...",
     ...
   }
   ```

#### Option B: Using Environment Variable
Alternatively, set as env var:
- **FIREBASE_CREDENTIALS_JSON**: `{"type":"service_account",...}` (entire JSON as string)
- Update `firebase_service.py` to read from env var instead of file

### 4. Deploy!

1. Click **"Create Web Service"**
2. Wait 3-5 minutes for build and deployment
3. Your API will be available at: `https://synapse-ai-api.onrender.com`

### 5. Test the Deployment

```bash
curl https://synapse-ai-api.onrender.com/health
# Expected: {"status":"healthy","service":"Synapse AI API"}
```

### 6. Update Android App

Update `NetworkModule.kt`:

```kotlin
// Production URL
private const val BASE_URL = "https://synapse-ai-api.onrender.com/api/"
```

Rebuild and deploy Android app.

---

## Troubleshooting

### Build Fails
- Check build logs in Render dashboard
- Verify `requirements.txt` is in `backend/api/`
- Check Python version compatibility

### Service Crashes on Start
- Check service logs in Render
- Verify environment variables are set correctly
- Ensure Firebase credentials file exists

### API Returns 500 Errors
- Check service logs
- Verify OpenAI API key is valid
- Check Firebase credentials are correct

### Cold Starts (Free Plan)
- Free plan spins down after 15 min of inactivity
- First request after spin-down takes ~30 seconds
- Consider upgrading to paid plan for always-on service

---

## Monitoring

- **Logs**: https://dashboard.render.com → Your Service → Logs
- **Metrics**: https://dashboard.render.com → Your Service → Metrics
- **Health Check**: Render pings `/health` every 5 minutes

## Next Steps

1. ✅ Deploy successful
2. Test AI summarization from Android app
3. Monitor performance and errors
4. Consider upgrading plan if needed
5. Implement remaining AI features (Action Items, Priority, etc.)

