/**
 * Script to insert a realistic crypto project conversation into group "CryptoProject"
 * 52 messages between 3 participants discussing crypto trading feature
 * 
 * Run: node insert-crypto-conversation.js
 */

const admin = require('firebase-admin');
const path = require('path');

// Initialize Firebase Admin
const serviceAccount = require(path.join(__dirname, '../../backend/api/firebase-credentials.json'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// Conversation messages (52 messages) - English
const messages = [
  { sender: 'A', text: 'Hey team, let\'s start planning the new crypto trading feature! 🚀' },
  { sender: 'A', text: 'The goal is to allow users to buy and sell Bitcoin, Ethereum and other cryptos directly in the app' },
  { sender: 'B', text: 'Great! I\'ve been looking at some APIs we could integrate. Coinbase or Binance?' },
  { sender: 'C', text: 'From a UX perspective, we need to keep it really simple. Our users aren\'t technical' },
  { sender: 'A', text: 'Exactly! Simplicity is key. What timeline do you think is reasonable?' },
  { sender: 'B', text: 'Depends on the integration complexity. I\'ll need about 3 days just to study the APIs' },
  { sender: 'C', text: 'In the meantime I can start working on the interface mockups' },
  { sender: 'A', text: 'Perfect. We need to deliver in 3 weeks, so let\'s stay organized' },
  { sender: 'B', text: '3 weeks is tight but doable. We\'ll need to focus on MVP first' },
  { sender: 'C', text: 'Agreed. What features are essential for the MVP?' },
  { sender: 'A', text: 'My list: 1) Buy crypto, 2) Sell crypto, 3) View balance, 4) Transaction history' },
  { sender: 'B', text: 'What about wallets? Hot wallet or cold wallet?' },
  { sender: 'A', text: 'Hot wallet to start. Cold wallet is phase 2' },
  { sender: 'C', text: 'Makes sense. I\'ll design the main screens today' },
  { sender: 'B', text: 'Regarding security, we need mandatory 2FA for transactions above $1000' },
  { sender: 'A', text: '100% agree. Security is non-negotiable' },
  { sender: 'C', text: 'Can I integrate Google Authenticator and SMS?' },
  { sender: 'B', text: 'Yes, but SMS is less secure. Prioritize the authenticator app' },
  { sender: 'A', text: 'Let\'s support both but recommend the app' },
  { sender: 'C', text: 'Cool. What about fees? How much are we charging?' },
  { sender: 'A', text: 'I benchmarked competitors. The average is 1.5% per transaction' },
  { sender: 'B', text: 'Is that just our fee or does it include exchange fees?' },
  { sender: 'A', text: 'Our fee. Exchange fees come on top' },
  { sender: 'C', text: 'We need to make that super clear in the UI. Transparency is important' },
  { sender: 'B', text: 'Agreed. I\'ll add an endpoint that calculates the total before confirming' },
  { sender: 'A', text: 'Great idea! Preview before executing' },
  { sender: 'C', text: 'Like a "Review Order" before "Confirm"?' },
  { sender: 'A', text: 'Exactly!' },
  { sender: 'B', text: 'Speaking of APIs, I think Binance has better documentation' },
  { sender: 'C', text: 'What about latency? Do we have an SLA?' },
  { sender: 'B', text: 'Transactions need to be processed in under 5 seconds' },
  { sender: 'A', text: '5 seconds is acceptable. But let\'s show clear loading states' },
  { sender: 'C', text: 'Already thought of that. Skeleton screens + progress indicator' },
  { sender: 'B', text: 'Perfect. What if the API goes down? We need retry logic' },
  { sender: 'A', text: 'Yes, but with a limit. Maximum 3 attempts' },
  { sender: 'B', text: 'And circuit breaker to avoid overload during instability' },
  { sender: 'C', text: 'Are you guys using websockets for real-time prices?' },
  { sender: 'B', text: 'Yes! Binance WebSocket updates every second' },
  { sender: 'A', text: 'That\'ll make the experience so much better' },
  { sender: 'C', text: 'I\'ll need to optimize re-renders then. Can\'t freeze the UI' },
  { sender: 'B', text: 'Use 500ms debounce so you don\'t process every second' },
  { sender: 'C', text: 'Good call! I\'ll do that' },
  { sender: 'A', text: 'About compliance, do we need to verify KYC before allowing trades?' },
  { sender: 'B', text: 'Yes, it\'s required by law. Above $10k per month needs full verification' },
  { sender: 'C', text: 'So I need a document upload screen' },
  { sender: 'A', text: 'Correct. ID, proof of address, selfie' },
  { sender: 'B', text: 'Should I integrate with a verification service like Onfido?' },
  { sender: 'A', text: 'Yes, we already have a contract with them. Just needs the integration' },
  { sender: 'C', text: 'Cool, I\'ll add it to the mockups' },
  { sender: 'B', text: 'Guys, let\'s have a meeting tomorrow at 10am to align on technical details?' },
  { sender: 'A', text: 'Works for me! I\'ll prepare the requirements doc by then' },
  { sender: 'C', text: 'Confirmed! I\'ll have the mockups ready to show' }
];

async function insertConversation() {
  try {
    console.log('🔍 Finding group "CryptoProject"...\n');

    // Find group "CryptoProject"
    const conversationsSnapshot = await db.collection('conversations')
      .where('groupName', '==', 'CryptoProject')
      .get();

    let groupId = null;
    let groupData = null;

    if (conversationsSnapshot.size > 0) {
      const doc = conversationsSnapshot.docs[0];
      groupId = doc.id;
      groupData = doc.data();
    }

    if (!groupId) {
      console.error('❌ Group "CryptoProject" not found!');
      console.log('Please create a group named "CryptoProject" first.');
      process.exit(1);
    }

    console.log('✅ Found group "CryptoProject":', groupId);
    console.log('📋 Members:', groupData.memberIds);
    console.log('👥 Member count:', groupData.memberIds.length);

    if (groupData.memberIds.length !== 3) {
      console.error('❌ Group must have exactly 3 members!');
      console.log('Current members:', groupData.memberIds.length);
      process.exit(1);
    }

    const [userA, userB, userC] = groupData.memberIds;
    console.log('\n👤 Person A:', userA);
    console.log('👤 Person B:', userB);
    console.log('👤 Person C:', userC);

    // Map sender labels to actual user IDs
    const senderMap = {
      'A': userA,
      'B': userB,
      'C': userC
    };

    console.log('\n📝 Inserting 52 messages...\n');

    // Base timestamp (start from 2 hours ago)
    let timestamp = Date.now() - (2 * 60 * 60 * 1000);

    // Insert messages with realistic timing (1-3 minutes between messages)
    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i];
      const senderId = senderMap[msg.sender];

      // Add random delay between 30 seconds and 3 minutes
      timestamp += Math.floor(Math.random() * 150000) + 30000;

      const messageData = {
        text: msg.text,
        senderId: senderId,
        localTimestamp: admin.firestore.Timestamp.fromMillis(timestamp),
        memberIdsAtCreation: groupData.memberIds,
        serverTimestamp: admin.firestore.FieldValue.serverTimestamp(),
        type: 'text',
        sendNotification: true,
        isDeleted: false
      };

      await db.collection('conversations')
        .doc(groupId)
        .collection('messages')
        .add(messageData);

      console.log(`✅ [${i + 1}/52] ${msg.sender}: ${msg.text.substring(0, 50)}...`);
    }

    // Update conversation metadata with last message
    const lastMessage = messages[messages.length - 1];
    await db.collection('conversations').doc(groupId).update({
      lastMessageText: lastMessage.text,
      updatedAtMs: timestamp
    });

    console.log('\n🎉 Successfully inserted 52 messages into group "CryptoProject"!');
    console.log('📊 Conversation spans approximately 2 hours');
    console.log('\n✨ You can now test AI Summarization and Smart Search on this conversation!');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

insertConversation();

