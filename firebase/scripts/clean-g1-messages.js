/**
 * Clean all old messages from group g1 before inserting new ones
 */

const admin = require('firebase-admin');
const path = require('path');

// Initialize Firebase Admin
const serviceAccount = require(path.join(__dirname, '../../backend/api/firebase-credentials.json'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function cleanMessages() {
  try {
    console.log('🔍 Finding group "g1"...\n');

    const snapshot = await db.collection('conversations')
      .where('groupName', '==', 'g1')
      .get();

    if (snapshot.empty) {
      console.error('❌ Group "g1" not found!');
      process.exit(1);
    }

    const groupId = snapshot.docs[0].id;
    console.log('✅ Found group:', groupId);

    // Get all messages
    const messagesSnapshot = await db.collection('conversations')
      .doc(groupId)
      .collection('messages')
      .get();

    console.log(`\n🗑️  Found ${messagesSnapshot.size} messages to delete...\n`);

    // Delete all messages
    const batch = db.batch();
    messagesSnapshot.docs.forEach(doc => {
      batch.delete(doc.ref);
      console.log(`  ❌ Deleting: ${doc.data().text?.substring(0, 50)}...`);
    });

    await batch.commit();

    console.log('\n✅ All messages deleted!');
    console.log('🎉 Group "g1" is clean. Now run: node insert-crypto-conversation.js');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

cleanMessages();

