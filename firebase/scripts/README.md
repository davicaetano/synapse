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

## Usage

After creating users, you can login to the Android app with any of these credentials.

**Note:** Add `service-account-key.json` to `.gitignore` (already done).

