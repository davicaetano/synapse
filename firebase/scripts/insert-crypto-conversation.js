/**
 * Script to insert a realistic crypto project conversation into group "g1"
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

// Conversation messages (52 messages)
const messages = [
  { sender: 'A', text: 'Pessoal, vamos começar o planejamento da nova feature de criptomoedas! 🚀' },
  { sender: 'A', text: 'O objetivo é permitir que usuários comprem e vendam Bitcoin, Ethereum e outras cryptos direto no app' },
  { sender: 'B', text: 'Boa! Já pensei em algumas APIs que podemos integrar. Coinbase ou Binance?' },
  { sender: 'C', text: 'Do ponto de vista de UX, temos que deixar bem simples. O público não é técnico' },
  { sender: 'A', text: 'Exato! Simplicidade é chave. Qual timeline vocês acham razoável?' },
  { sender: 'B', text: 'Depende da complexidade da integração. Vou precisar de uns 3 dias só pra estudar as APIs' },
  { sender: 'C', text: 'Enquanto isso posso começar os mockups da interface' },
  { sender: 'A', text: 'Perfeito. Temos que entregar em 3 semanas, então vamos ser bem organizados' },
  { sender: 'B', text: '3 semanas é apertado mas dá. Vamos precisar focar no MVP primeiro' },
  { sender: 'C', text: 'Concordo. Quais features são essenciais para o MVP?' },
  { sender: 'A', text: 'Minha lista: 1) Comprar crypto, 2) Vender crypto, 3) Ver saldo, 4) Histórico de transações' },
  { sender: 'B', text: 'E carteira? Vamos usar hot wallet ou cold wallet?' },
  { sender: 'A', text: 'Hot wallet para começar. Cold wallet é fase 2' },
  { sender: 'C', text: 'Faz sentido. Vou desenhar as telas principais hoje' },
  { sender: 'B', text: 'Sobre segurança, precisamos de 2FA obrigatório para transações acima de $1000' },
  { sender: 'A', text: 'Concordo 100%. Segurança não é negociável' },
  { sender: 'C', text: 'Posso integrar Google Authenticator e SMS?' },
  { sender: 'B', text: 'Sim, mas SMS é menos seguro. Prioriza o authenticator app' },
  { sender: 'A', text: 'Vamos suportar os dois mas recomendar o app' },
  { sender: 'C', text: 'Beleza. E sobre as taxas? Vamos cobrar quanto?' },
  { sender: 'A', text: 'Benchmarkei os concorrentes. A média é 1.5% por transação' },
  { sender: 'B', text: 'Isso é apenas nossa taxa ou inclui as taxas da exchange?' },
  { sender: 'A', text: 'Nossa taxa. As taxas da exchange vêm por cima' },
  { sender: 'C', text: 'Precisa deixar isso bem claro na UI. Transparência é importante' },
  { sender: 'B', text: 'Concordo. Vou adicionar um endpoint que calcula o total antes de confirmar' },
  { sender: 'A', text: 'Ótima ideia! Preview antes de executar' },
  { sender: 'C', text: 'Tipo um "Review Order" antes do "Confirm"?' },
  { sender: 'A', text: 'Exatamente!' },
  { sender: 'B', text: 'Falando em APIs, acho que Binance tem melhor documentação' },
  { sender: 'C', text: 'E a latência? Temos SLA?' },
  { sender: 'B', text: 'Transações precisam ser processadas em menos de 5 segundos' },
  { sender: 'A', text: '5 segundos é aceitável. Mas vamos mostrar loading states bem claros' },
  { sender: 'C', text: 'Já pensei nisso. Skeleton screens + progress indicator' },
  { sender: 'B', text: 'Perfeito. E se a API cair? Precisamos de retry logic' },
  { sender: 'A', text: 'Sim, mas com limite. Máximo 3 tentativas' },
  { sender: 'B', text: 'E circuit breaker para não sobrecarregar se tiver instabilidade' },
  { sender: 'C', text: 'Vocês vão usar websockets para preços em tempo real?' },
  { sender: 'B', text: 'Sim! WebSocket da Binance atualiza a cada segundo' },
  { sender: 'A', text: 'Isso vai deixar a experiência muito melhor' },
  { sender: 'C', text: 'Vou precisar otimizar os re-renders então. Não pode travar a UI' },
  { sender: 'B', text: 'Use debounce de 500ms pra não processar todo segundo' },
  { sender: 'C', text: 'Boa! Vou fazer isso' },
  { sender: 'A', text: 'Sobre compliance, precisamos verificar KYC antes de permitir trades?' },
  { sender: 'B', text: 'Sim, é obrigatório por lei. Acima de $10k por mês precisa verificação completa' },
  { sender: 'C', text: 'Então preciso de uma tela de upload de documentos' },
  { sender: 'A', text: 'Correto. ID, comprovante de residência, selfie' },
  { sender: 'B', text: 'Vou integrar com algum serviço de verificação tipo Onfido?' },
  { sender: 'A', text: 'Sim, já temos contrato com eles. Só precisa da integração' },
  { sender: 'C', text: 'Beleza, vou adicionar nos mockups' },
  { sender: 'B', text: 'Pessoal, vamos fazer uma reunião amanhã 10h pra alinhar detalhes técnicos?' },
  { sender: 'A', text: 'Pode ser! Vou preparar o doc de requisitos até lá' },
  { sender: 'C', text: 'Confirmo! Já vou ter os mockups prontos pra mostrar' }
];

async function insertConversation() {
  try {
    console.log('🔍 Finding group "g1"...\n');

    // Find group "g1"
    const conversationsSnapshot = await db.collection('conversations')
      .where('groupName', '==', 'g1')
      .get();

    let groupId = null;
    let groupData = null;

    if (conversationsSnapshot.size > 0) {
      const doc = conversationsSnapshot.docs[0];
      groupId = doc.id;
      groupData = doc.data();
    }

    if (!groupId) {
      console.error('❌ Group "g1" not found!');
      console.log('Please create a group named "g1" first.');
      process.exit(1);
    }

    console.log('✅ Found group "g1":', groupId);
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
        createdAtMs: timestamp,
        memberIdsAtCreation: groupData.memberIds,
        serverTimestamp: admin.firestore.FieldValue.serverTimestamp()
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

    console.log('\n🎉 Successfully inserted 52 messages into group "g1"!');
    console.log('📊 Conversation spans approximately 2 hours');
    console.log('\n✨ You can now test AI Summarization on this conversation!');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  }
}

insertConversation();

