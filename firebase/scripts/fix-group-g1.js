/**
 * Fix group "g1" - Update field name from 'type' to 'convType'
 */

const admin = require('firebase-admin');
const path = require('path');

// Initialize Firebase Admin
const serviceAccount = require(path.join(__dirname, '../../backend/api/firebase-credentials.json'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function fixGroup() {
  try {
    console.log('🔍 Finding group "g1"...\n');

    // Find group g1
    const snapshot = await db.collection('conversations')
      .where('groupName', '==', 'g1')
      .get();

    if (snapshot.empty) {
      console.error('❌ Group "g1" not found!');
      process.exit(1);
    }

    const doc = snapshot.docs[0];
    console.log('✅ Found group:', doc.id);
    console.log('Current data:', doc.data());

    // Update the field
    console.log('\n🔧 Fixing convType field...');
    await db.collection('conversations').doc(doc.id).update({
      convType: 'GROUP'
    });

    console.log('✅ Fixed! Group now has convType: GROUP');
    console.log('\n🎉 Group "g1" is ready! Check your app now.');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

fixGroup();

