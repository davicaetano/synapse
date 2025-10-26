/**
 * Script to insert SpaceX Starship project conversation
 * 50 messages between 3 team members discussing rocket development
 * 
 * OPTIMIZED for testing AI Features 4 & 5:
 * - Priority Detection: URGENT, ASAP, BLOCKER keywords
 * - Decision Tracking: Explicit decisions with confirmations
 * 
 * Run: node insert-spacex-conversation.js
 */

const admin = require('firebase-admin');
const path = require('path');

// Initialize Firebase Admin
const serviceAccount = require(path.join(__dirname, '../../backend/api/firebase-credentials.json'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// Conversation messages (50 messages) - English
// Team: Sarah (Project Lead), Alex (Propulsion Engineer), Maria (Avionics Lead)
const messages = [
  // Introduction & Planning (Messages 1-8)
  { sender: 'A', text: 'Team, we need to finalize the Starship V3 design this week! Launch window opens in 6 months 🚀' },
  { sender: 'B', text: 'Excited! I\'ve been working on the Raptor 3 engine specs. Major improvements over V2' },
  { sender: 'C', text: 'Avionics team is ready. We have the new flight computer prototypes' },
  { sender: 'A', text: 'Perfect timing. What\'s our biggest risk right now?' },
  { sender: 'B', text: 'Heat shield tiles. The reentry test last month showed 15% failure rate' },
  { sender: 'C', text: 'That\'s concerning. What\'s the acceptable threshold?' },
  { sender: 'A', text: 'Less than 2%. We CANNOT launch with current failure rate' },
  { sender: 'B', text: 'Agreed. I\'ll schedule additional thermal tests this week' },
  
  // URGENT Issues Start (Messages 9-16)
  { sender: 'B', text: '🚨 URGENT: Pressure test on Tank 7 just failed at 80% capacity' },
  { sender: 'A', text: 'What?! That\'s a BLOCKER for the timeline. When did this happen?' },
  { sender: 'B', text: '10 minutes ago. The weld on the bulkhead cracked under pressure' },
  { sender: 'C', text: 'Do we need to halt production on the other tanks?' },
  { sender: 'A', text: 'Yes, IMMEDIATELY. This could affect all 12 tanks if it\'s a systemic issue' },
  { sender: 'B', text: 'I\'m calling the welding team right now. Need to inspect every single weld ASAP' },
  { sender: 'C', text: 'Should I notify Elon about this?' },
  { sender: 'A', text: 'Yes, he needs to know. This is a CRITICAL failure that could delay launch by months' },
  
  // Decision Making & Resolution (Messages 17-25)
  { sender: 'B', text: 'Update: Found the issue. The welding robot had incorrect calibration for stainless steel 304L' },
  { sender: 'A', text: 'So it\'s fixable? What\'s the solution?' },
  { sender: 'B', text: 'We need to redo all welds on Tanks 5-12. Estimated 3 weeks of work' },
  { sender: 'C', text: 'Can we parallelize? Use multiple welding teams?' },
  { sender: 'A', text: 'Good idea. Let\'s bring in the Boca Chica team to help. What do you think?' },
  { sender: 'B', text: 'Sounds good! That would cut it to 10 days' },
  { sender: 'C', text: 'Agreed. I\'ll coordinate the logistics' },
  { sender: 'A', text: 'Perfect. DECISION: We\'ll use both teams and target 10-day completion. Everyone on board?' },
  { sender: 'B', text: 'Yes, let\'s do it!' },
  { sender: 'C', text: '👍 Approved. I\'ll send the request to HR today' },
  
  // Technical Discussions (Messages 26-35)
  { sender: 'C', text: 'Moving to avionics - we need to decide on the flight computer redundancy' },
  { sender: 'A', text: 'What are the options?' },
  { sender: 'C', text: 'Option A: Triple redundancy (3 computers, 99.99% reliability, $2M extra)' },
  { sender: 'C', text: 'Option B: Dual redundancy (2 computers, 99.9% reliability, $800K extra)' },
  { sender: 'B', text: 'For crewed missions, I vote triple. The extra cost is worth it' },
  { sender: 'A', text: 'I agree. Human life is priceless. Let\'s go with triple redundancy' },
  { sender: 'C', text: 'Confirmed! I\'ll order the additional hardware this afternoon' },
  { sender: 'A', text: 'DECISION MADE: Triple redundancy for flight computers. Approved by all' },
  { sender: 'B', text: 'What about the Raptor engine gimbal? Are we sticking with hydraulic or going electric?' },
  { sender: 'A', text: 'Good question. What\'s your recommendation?' },
  { sender: 'B', text: 'Electric is lighter (200kg savings) and more reliable. Hydraulics are proven but heavier' },
  { sender: 'C', text: 'Weight savings would give us 500kg extra payload capacity' },
  { sender: 'A', text: 'That\'s significant. Let\'s switch to electric actuators' },
  { sender: 'B', text: 'Agreed! I\'ll update the design docs' },
  
  // More URGENT Items (Messages 36-42)
  { sender: 'C', text: '⚠️ URGENT: Software team just reported a CRITICAL bug in the guidance system' },
  { sender: 'A', text: 'What kind of bug? Is it a BLOCKER?' },
  { sender: 'C', text: 'Yes! The trajectory calculation fails above Mach 15. Ship would miss landing zone by 50km' },
  { sender: 'B', text: 'That\'s catastrophic! When is the fix coming?' },
  { sender: 'C', text: 'They\'re working on it now. Need it ASAP before the simulation tomorrow' },
  { sender: 'A', text: 'This is HIGHEST PRIORITY. Cancel all other meetings. Focus on this bug' },
  { sender: 'C', text: 'Copy that. Pulling in 5 more engineers to debug IMMEDIATELY' },
  
  // Final Decisions & Wrap-up (Messages 43-50)
  { sender: 'C', text: 'Update: Bug fixed! It was a floating-point precision error in the nav code' },
  { sender: 'A', text: 'Excellent work! Let\'s do final review tomorrow at 9am' },
  { sender: 'B', text: 'Before we wrap up, do we have consensus on the launch date?' },
  { sender: 'A', text: 'Proposed: March 15th, 2025. That gives us 6 months' },
  { sender: 'C', text: 'Works for me. Avionics will be ready by February' },
  { sender: 'B', text: 'Propulsion systems will be done by end of January. March 15 is doable' },
  { sender: 'A', text: 'FINAL DECISION: Starship V3 launch date is March 15, 2025. Agreed?' },
  { sender: 'B', text: 'Agreed! Let\'s make history 🚀' },
  { sender: 'C', text: '100% agreed. Let\'s do this!' },
  { sender: 'A', text: 'Meeting tomorrow at 9am sharp. Bring status reports. This is it, team! 🌟' }
];

async function insertConversation() {
  try {
    console.log('🔍 Finding group "SpaceX Starship"...\n');

    // Find group "SpaceX Starship"
    const conversationsSnapshot = await db.collection('conversations')
      .where('groupName', '==', 'SpaceX Starship')
      .get();

    let groupId = null;
    let groupData = null;

    if (conversationsSnapshot.size > 0) {
      const doc = conversationsSnapshot.docs[0];
      groupId = doc.id;
      groupData = doc.data();
    }

    if (!groupId) {
      console.error('❌ Group "SpaceX Starship" not found!');
      console.log('\n💡 Creating group "SpaceX Starship" first...\n');
      
      // Get first 3 users from Firestore to create the group
      const usersSnapshot = await db.collection('users').limit(3).get();
      
      if (usersSnapshot.size < 3) {
        console.error('❌ Need at least 3 users in Firestore!');
        console.log('Run: node create-test-users.js 5');
        process.exit(1);
      }

      const memberIds = usersSnapshot.docs.map(doc => doc.id);
      
      // Create the group
      const SYNAPSE_BOT_ID = 'synapse-bot-system';
      const now = admin.firestore.Timestamp.now();
      
      // Build memberStatus with real members + bot (bot NOT in memberIds!)
      const memberStatus = memberIds.reduce((acc, id) => {
        acc[id] = {
          lastSeenAt: 0,
          lastReceivedAt: 0,
          lastMessageSentAt: 0
        };
        return acc;
      }, {});
      
      // Add bot to memberStatus (but NOT to memberIds)
      memberStatus[SYNAPSE_BOT_ID] = {
        lastSeenAt: now,
        lastReceivedAt: now,
        lastMessageSentAt: now  // ✅ This allows Android to find lastMessageSentAt
      };
      
      const newGroupRef = await db.collection('conversations').add({
        convType: 'GROUP',  // ✅ FIXED: Android expects 'convType', not 'type'
        groupName: 'SpaceX Starship',
        memberIds: memberIds,  // ✅ Bot NOT in memberIds (can't be removed)
        createdBy: memberIds[0],  // First user is the creator
        createdAtMs: Date.now(),
        updatedAtMs: Date.now(),
        lastMessageText: '',
        memberStatus: memberStatus  // ✅ Bot IS in memberStatus (for lastMessageSentAt)
      });

      groupId = newGroupRef.id;
      groupData = { memberIds };
      
      console.log('✅ Created group "SpaceX Starship":', groupId);
    } else {
      console.log('✅ Found group "SpaceX Starship":', groupId);
    }

    console.log('📋 Members:', groupData.memberIds);
    console.log('👥 Member count:', groupData.memberIds.length);

    if (groupData.memberIds.length < 3) {
      console.error('❌ Group must have at least 3 members!');
      console.log('Current members:', groupData.memberIds.length);
      process.exit(1);
    }

    const [userA, userB, userC] = groupData.memberIds;
    console.log('\n👤 Sarah (Project Lead):', userA);
    console.log('👤 Alex (Propulsion):', userB);
    console.log('👤 Maria (Avionics):', userC);

    // Map sender labels to actual user IDs
    const senderMap = {
      'A': userA,
      'B': userB,
      'C': userC
    };

    console.log('\n📝 Inserting 50 messages...\n');
    console.log('🎯 Optimized for AI testing:');
    console.log('   - Priority Detection: 8 URGENT/BLOCKER messages');
    console.log('   - Decision Tracking: 5 explicit decisions\n');

    // Base timestamp (start from 3 hours ago)
    let timestamp = Date.now() - (3 * 60 * 60 * 1000);

    // Insert messages with realistic timing (30s to 4 minutes between messages)
    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i];
      const senderId = senderMap[msg.sender];

      // Add random delay between 30 seconds and 4 minutes
      timestamp += Math.floor(Math.random() * 210000) + 30000;

      const messageData = {
        text: msg.text,
        senderId: senderId,
        createdAtMs: timestamp,
        memberIdsAtCreation: groupData.memberIds,
        serverTimestamp: admin.firestore.FieldValue.serverTimestamp()
      };

      await db.collection('conversations')
        .doc(groupId)
        .collection('messages')
        .add(messageData);

      // Show preview (truncate long messages)
      const preview = msg.text.length > 60 ? msg.text.substring(0, 60) + '...' : msg.text;
      const icon = msg.text.includes('URGENT') || msg.text.includes('BLOCKER') ? '🚨' : 
                   msg.text.includes('DECISION') || msg.text.includes('Agreed') ? '📋' : '💬';
      
      console.log(`${icon} [${i + 1}/50] ${msg.sender}: ${preview}`);
    }

    // Update conversation metadata with last message
    const lastMessage = messages[messages.length - 1];
    await db.collection('conversations').doc(groupId).update({
      lastMessageText: lastMessage.text,
      updatedAtMs: timestamp
    });

    console.log('\n🎉 Successfully inserted 50 messages into group "SpaceX Starship"!');
    console.log('📊 Conversation spans approximately 3 hours');
    console.log('\n🚀 AI TESTING COVERAGE:');
    console.log('   ✅ Feature 1: Thread Summarization - Full conversation');
    console.log('   ✅ Feature 2: Action Items - Multiple tasks with deadlines');
    console.log('   ✅ Feature 3: Smart Search - Technical terms (Raptor, Mach 15, etc)');
    console.log('   ✅ Feature 4: Priority Detection - 8 URGENT/BLOCKER messages');
    console.log('   ✅ Feature 5: Decision Tracking - 5 explicit decisions');
    console.log('\n✨ Ready to test all AI features!');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

insertConversation();

