# Initial Setup - Step 00: Directory Structure

Purpose: Define and create the base repo layout to align with the PRD and enable parallel workstreams.

## Directories Created
- android/
  - app/src/main/java/com/synapse/ui
  - app/src/main/java/com/synapse/data/firestore
  - app/src/main/java/com/synapse/data/realtime
  - app/src/main/java/com/synapse/data/local
  - app/src/main/java/com/synapse/domain
  - app/src/main/java/com/synapse/network
  - app/src/main/res
  - gradle/
- backend/
  - api/agent
  - api/routers
- firebase/

## Rationale
- Mirrors PRD structure to keep Android, backend, and Firebase concerns isolated.
- Enables us to bootstrap Android and write backend AI endpoints in parallel.
- Avoids committing secrets later by reserving dedicated folders for environment-specific files.

## Next Steps
1. Step 01 - Android bootstrap (Compose, Firebase SDKs, FCM)
2. Later - Backend FastAPI scaffold for AI endpoints (/ai/*)
3. Later - Firebase rules and indexes
