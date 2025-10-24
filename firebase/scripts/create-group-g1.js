/**
 * Script to create group "g1" with 3 members for testing
 * Run: node create-group-g1.js
 */

const admin = require('firebase-admin');
const path = require('path');

// Initialize Firebase Admin
const serviceAccount = require(path.join(__dirname, '../../backend/api/firebase-credentials.json'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function createGroup() {
  try {
    console.log('🔍 Creating group with specified users...\n');

    // Hardcoded user IDs
    const memberIds = [
      'CvnL1uK3WaYDEX7boDc3TyHrSPY2',
      'XlylnTcLSeawP3GaspFDYVwFnoj2',
      'fOfbZadtNTXiBGwb8zldQk4Z4Lv2'
    ];
    
    // Fetch user details for display
    const memberNames = [];
    for (const userId of memberIds) {
      const userDoc = await db.collection('users').doc(userId).get();
      if (userDoc.exists) {
        const userData = userDoc.data();
        memberNames.push(userData.displayName || userData.email);
      } else {
        memberNames.push(userId);
      }
    }

    console.log('👥 Selected members:');
    memberNames.forEach((name, i) => {
      console.log(`   ${String.fromCharCode(65 + i)}) ${name} (${memberIds[i]})`);
    });

    console.log('\n📝 Creating group "g1"...');

    // Create group conversation
    const groupData = {
      convType: 'GROUP',  // ✅ FIXED: Was 'type: group'
      groupName: 'g1',
      memberIds: memberIds,
      createdBy: memberIds[0], // First user is the creator
      createdAtMs: Date.now(),
      updatedAtMs: Date.now(),
      lastMessageText: '',
      memberStatus: {}
    };

    // Initialize memberStatus for each member
    memberIds.forEach(memberId => {
      groupData.memberStatus[memberId] = {
        lastSeenAt: 0,
        lastReceivedAt: 0
      };
    });

    const groupRef = await db.collection('conversations').add(groupData);

    console.log('✅ Group "g1" created successfully!');
    console.log('📋 Group ID:', groupRef.id);
    console.log('👥 Members:', memberIds.length);
    console.log('\n🎉 Ready to insert conversation messages!');
    console.log('Run: node insert-crypto-conversation.js');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

createGroup();

