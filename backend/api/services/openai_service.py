"""
LangChain-powered AI service for intelligent features
"""

import os
from typing import List, Dict, Any
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser
from langchain_core.runnables import RunnablePassthrough
from models.schemas import Message, ActionItem, Decision

# Load environment variables
load_dotenv()

# Initialize LangChain ChatOpenAI
# Using GPT-3.5-turbo for fast responses (~2-3s for 30 messages)
# GPT-4 is too slow for real-time chat summaries (~10s for 30 messages)
llm = ChatOpenAI(
    model="gpt-3.5-turbo",
    temperature=0.3,
    api_key=os.getenv("OPENAI_API_KEY")
)

# ============================================================
# THREAD SUMMARIZATION
# ============================================================

async def summarize_thread(messages: List[Message], custom_instructions: str = None) -> Dict[str, Any]:
    """
    Generate a comprehensive summary of conversation thread using LangChain
    Supports custom instructions for focused summaries
    """
    # Build conversation context
    conversation_text = "\n".join([
        f"[{msg.created_at.strftime('%Y-%m-%d %H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # Build user prompt with optional custom instructions
    user_prompt = """Analyze this conversation and provide:
1. A concise overall summary (2-3 sentences)
2. Key points discussed (bullet points)"""
    
    if custom_instructions:
        user_prompt += f"\n\n**Special Instructions:** {custom_instructions}"
    
    user_prompt += """

Conversation:
{conversation}

Respond in JSON format:
{{
    "summary": "overall summary here",
    "key_points": ["point 1", "point 2", ...]
}}"""
    
    # Create prompt template
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are an expert at summarizing team conversations for remote professionals."),
        ("user", user_prompt)
    ])
    
    # Create chain with JSON output
    parser = JsonOutputParser()
    chain = prompt | llm | parser
    
    # Invoke chain
    result = await chain.ainvoke({"conversation": conversation_text})
    return result

# ============================================================
# ACTION ITEMS EXTRACTION
# ============================================================

async def extract_action_items(messages: List[Message]) -> List[ActionItem]:
    """
    Extract action items, tasks, and todos from conversation using LangChain
    """
    conversation_text = "\n".join([
        f"[MSG_ID:{msg.id}] [{msg.created_at.strftime('%H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # Create prompt template
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are an expert at identifying action items and tasks in team conversations."),
        ("user", """Extract ALL action items, tasks, todos, and commitments from this conversation.

Look for:
- Direct assignments ("John, can you...", "@Sarah please...")
- Commitments ("I'll do X by Friday")
- Todos ("We need to...", "Someone should...")
- Deadlines and due dates

Conversation:
{conversation}

For each action item, extract:
- task: clear description
- assigned_to: person's name (or null if unclear)
- deadline: any mentioned date/time (or null)
- priority: low/medium/high based on urgency
- message_id: the MSG_ID where it was mentioned
- context: relevant surrounding text

Respond in JSON format:
{{
    "action_items": [
        {{
            "task": "task description",
            "assigned_to": "person name or null",
            "deadline": "deadline or null",
            "priority": "medium",
            "message_id": "MSG_ID",
            "context": "surrounding text"
        }}
    ]
}}""")
    ])
    
    # Create chain
    llm_low_temp = ChatOpenAI(model="gpt-3.5-turbo", temperature=0.2)
    parser = JsonOutputParser()
    chain = prompt | llm_low_temp | parser
    
    # Invoke chain
    result = await chain.ainvoke({"conversation": conversation_text})
    
    # Convert to ActionItem models
    action_items = []
    for item in result.get("action_items", []):
        action_items.append(ActionItem(
            task=item["task"],
            assigned_to=item.get("assigned_to"),
            deadline=item.get("deadline"),
            priority=item.get("priority", "medium"),
            mentioned_in_message_id=item["message_id"],
            context=item["context"]
        ))
    
    return action_items

# ============================================================
# SMART SEMANTIC SEARCH
# ============================================================

async def semantic_search(query: str, messages: List[Message], max_results: int = 10) -> List[Dict[str, Any]]:
    """
    Perform semantic search using RAG pipeline with LangChain + ChromaDB
    """
    # Use RAG service for true semantic search with embeddings
    from services.rag_service import hybrid_search
    
    # Perform hybrid search (semantic + LLM reranking)
    results = await hybrid_search(query, messages, max_results)
    
    return results

# ============================================================
# PRIORITY DETECTION
# ============================================================

async def detect_priority(messages: List[Message]) -> List[Dict[str, Any]]:
    """
    Detect urgent/high-priority messages using LangChain
    """
    conversation_text = "\n".join([
        f"[MSG_ID:{msg.id}] [{msg.created_at.strftime('%H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # Create prompt template
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are an expert at identifying urgent and high-priority messages in team conversations."),
        ("user", """Identify messages that are:
- Urgent (ASAP, immediate, today, deadline)
- Blocking issues
- Questions that need quick answers
- Critical decisions
- Important announcements

Conversation:
{conversation}

For each priority message, provide:
- message_id
- priority_score: 0.0 to 1.0 (1.0 = most urgent)
- urgency_level: low/medium/high/urgent
- reasons: list of why it's prioritized

Respond in JSON format:
{{
    "priority_messages": [
        {{
            "message_id": "MSG_ID",
            "priority_score": 0.9,
            "urgency_level": "urgent",
            "reasons": ["contains deadline", "blocking issue"]
        }}
    ]
}}""")
    ])
    
    # Create chain
    llm_low_temp = ChatOpenAI(model="gpt-3.5-turbo", temperature=0.2)
    parser = JsonOutputParser()
    chain = prompt | llm_low_temp | parser
    
    # Invoke chain
    result = await chain.ainvoke({"conversation": conversation_text})
    return result.get("priority_messages", [])

# ============================================================
# DECISION TRACKING
# ============================================================

async def track_decisions(messages: List[Message]) -> List[Decision]:
    """
    Identify decisions made in conversation using LangChain
    """
    conversation_text = "\n".join([
        f"[MSG_ID:{msg.id}] [{msg.created_at.strftime('%H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # Create prompt template
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are an expert at identifying decisions made in team conversations."),
        ("user", """Look for:
- Explicit decisions ("we decided to...", "let's go with...")
- Agreements ("sounds good", "agreed", "👍")
- Consensus ("everyone okay with...?", "yes", "approved")
- Resolutions ("we'll do X", "final decision is...")

Conversation:
{conversation}

For each decision, extract:
- decision: clear statement of what was decided
- decided_by: list of people who agreed (names)
- timestamp: when it was decided
- confidence: 0.0 to 1.0 (how certain this is a decision)
- context: relevant surrounding messages
- message_ids: array of MSG_IDs involved

Respond in JSON format:
{{
    "decisions": [
        {{
            "decision": "decision statement",
            "decided_by": ["name1", "name2"],
            "timestamp": "time",
            "confidence": 0.85,
            "context": "surrounding text",
            "message_ids": ["MSG_ID1", "MSG_ID2"]
        }}
    ]
}}""")
    ])
    
    # Create chain
    llm_low_temp = ChatOpenAI(model="gpt-3.5-turbo", temperature=0.2)
    parser = JsonOutputParser()
    chain = prompt | llm_low_temp | parser
    
    # Invoke chain
    result = await chain.ainvoke({"conversation": conversation_text})
    
    # Convert to Decision models
    decisions = []
    for item in result.get("decisions", []):
        decisions.append(Decision(
            decision=item["decision"],
            decided_by=item["decided_by"],
            timestamp=item["timestamp"],
            confidence=item["confidence"],
            context=item["context"],
            message_ids=item["message_ids"]
        ))
    
    return decisions

