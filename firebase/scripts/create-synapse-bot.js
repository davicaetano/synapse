/**
 * Script to create Synapse Bot user
 * This bot sends welcome messages to new conversations
 * 
 * Run: node create-synapse-bot.js
 */

const admin = require('firebase-admin');
const path = require('path');

// Initialize Firebase Admin
const serviceAccount = require(path.join(__dirname, '../../backend/api/firebase-credentials.json'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const auth = admin.auth();
const db = admin.firestore();

const SYNAPSE_BOT = {
  uid: 'synapse-bot-system',
  email: 'bot@synapse.app',
  displayName: 'Synapse',
  photoURL: 'https://api.dicebear.com/7.x/bottts/svg?seed=synapse&backgroundColor=6366f1',
  password: 'ThisIsASystemBot-NoLogin-' + Math.random().toString(36)
};

async function createSynapseBot() {
  try {
    console.log('🤖 Creating Synapse Bot...\n');

    // Step 1: Create user in Firebase Auth
    try {
      const userRecord = await auth.createUser({
        uid: SYNAPSE_BOT.uid,
        email: SYNAPSE_BOT.email,
        displayName: SYNAPSE_BOT.displayName,
        photoURL: SYNAPSE_BOT.photoURL,
        password: SYNAPSE_BOT.password,
        emailVerified: true
      });
      
      console.log('✅ Auth user created:', userRecord.uid);
    } catch (error) {
      if (error.code === 'auth/uid-already-exists') {
        console.log('⚠️  Auth user already exists, updating...');
        await auth.updateUser(SYNAPSE_BOT.uid, {
          displayName: SYNAPSE_BOT.displayName,
          photoURL: SYNAPSE_BOT.photoURL
        });
      } else {
        throw error;
      }
    }

    // Step 2: Create user document in Firestore
    await db.collection('users').doc(SYNAPSE_BOT.uid).set({
      id: SYNAPSE_BOT.uid,
      email: SYNAPSE_BOT.email,
      displayName: SYNAPSE_BOT.displayName,
      photoUrl: SYNAPSE_BOT.photoURL,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      isSystemBot: true // Special flag to identify bot
    }, { merge: true });

    console.log('✅ Firestore document created\n');

    console.log('🎉 Synapse Bot created successfully!\n');
    console.log('📋 Bot Details:');
    console.log('   UID:', SYNAPSE_BOT.uid);
    console.log('   Email:', SYNAPSE_BOT.email);
    console.log('   Name:', SYNAPSE_BOT.displayName);
    console.log('   Avatar:', SYNAPSE_BOT.photoURL);

    process.exit(0);
  } catch (error) {
    console.error('❌ Error creating Synapse Bot:', error);
    process.exit(1);
  }
}

createSynapseBot();

