/**
 * Script to create group "CryptoProject" with 3 members for testing
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

    console.log('\n📝 Creating group "CryptoProject"...');

    // Create group conversation with new schema
    const now = admin.firestore.Timestamp.now();
    const SYNAPSE_BOT_ID = 'synapse-bot-system';
    
    // Build members map with real members + bot
    const members = {};
    
    // Add real members (first one is admin/creator)
    memberIds.forEach((id, index) => {
      members[id] = {
        lastSeenAt: now,
        lastReceivedAt: now,
        lastMessageSentAt: now,
        isBot: false,
        isAdmin: index === 0,  // First member is admin/creator
        isDeleted: false
      };
    });
    
    // Add bot to members (isBot: true, NOT in memberIds array)
    members[SYNAPSE_BOT_ID] = {
      lastSeenAt: now,
      lastReceivedAt: now,
      lastMessageSentAt: now,
      isBot: true,
      isAdmin: false,
      isDeleted: false
    };

    const groupData = {
      convType: 'GROUP',
      groupName: 'CryptoProject',
      memberIds: memberIds,  // Pre-populated for instant inbox visibility
      createdBy: memberIds[0],  // First user is the creator
      localTimestamp: now,  // Using Timestamp format (not milliseconds)
      updatedAt: now,
      lastMessageText: '',
      members: members  // NEW: Unified members map with isBot, isAdmin, isDeleted
    };

    const groupRef = await db.collection('conversations').add(groupData);

    console.log('✅ Group "CryptoProject" created successfully!');
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

