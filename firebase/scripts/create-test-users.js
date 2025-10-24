/**
 * Script to create test users in Firebase
 * 
 * Usage:
 *   node create-test-users.js [count]
 * 
 * Example:
 *   node create-test-users.js 10
 */

const admin = require('firebase-admin');
const serviceAccount = require('../service-account-key.json');

// Initialize Admin SDK
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const auth = admin.auth();

const testUsers = [
  { displayName: 'Alice Johnson', email: 'alice@test.com' },
  { displayName: 'Bob Smith', email: 'bob@test.com' },
  { displayName: 'Charlie Brown', email: 'charlie@test.com' },
  { displayName: 'Diana Prince', email: 'diana@test.com' },
  { displayName: 'Eve Martinez', email: 'eve@test.com' },
  { displayName: 'Frank Wilson', email: 'frank@test.com' },
  { displayName: 'Grace Lee', email: 'grace@test.com' },
  { displayName: 'Henry Davis', email: 'henry@test.com' },
  { displayName: 'Ivy Chen', email: 'ivy@test.com' },
  { displayName: 'Jack Ryan', email: 'jack@test.com' },
];

async function createTestUsers(count = 10) {
  console.log(`Creating ${count} test users...`);
  const password = 'Test123!';  // Default password for all test users
  
  for (let i = 0; i < Math.min(count, testUsers.length); i++) {
    const user = testUsers[i];
    
    try {
      // Create Auth user
      const userRecord = await auth.createUser({
        email: user.email,
        password: password,
        displayName: user.displayName,
        emailVerified: true
      });
      
      console.log(`✅ Created Auth user: ${user.displayName} (${userRecord.uid})`);
      
      // Create Firestore user document
      await db.collection('users').doc(userRecord.uid).set({
        displayName: user.displayName,
        email: user.email,
        photoUrl: `https://ui-avatars.com/api/?name=${encodeURIComponent(user.displayName)}&background=random`,
        createdAtMs: Date.now(),
        updatedAtMs: Date.now()
      });
      
      console.log(`✅ Created Firestore doc for ${user.displayName}`);
      
    } catch (error) {
      if (error.code === 'auth/email-already-exists') {
        console.log(`⚠️  User ${user.email} already exists, skipping...`);
      } else {
        console.error(`❌ Error creating ${user.email}:`, error.message);
      }
    }
  }
  
  console.log('\n✅ Done! Test users created.');
  console.log(`\nLogin credentials:`);
  console.log(`Email: alice@test.com (or any from the list)`);
  console.log(`Password: ${password}`);
  console.log('\nAll users:');
  testUsers.slice(0, count).forEach(u => console.log(`  - ${u.displayName} (${u.email})`));
  
  process.exit(0);
}

// Get count from command line args
const count = parseInt(process.argv[2]) || 10;
createTestUsers(count);

