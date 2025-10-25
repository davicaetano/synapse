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
    max_tokens=300,  # Limit output tokens for faster responses (~200 tokens = 1-2s)
    api_key=os.getenv("OPENAI_API_KEY")
)

# ============================================================
# THREAD SUMMARIZATION
# ============================================================

async def summarize_thread(messages: List[Message], custom_instructions: str = None) -> Dict[str, Any]:
    """
    Generate a comprehensive summary of conversation thread using LangChain
    Supports custom instructions for focused summaries or answering specific questions
    """
    # Build conversation context
    conversation_text = "\n".join([
        f"[{msg.created_at.strftime('%Y-%m-%d %H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # If custom instructions provided, treat it as a question/request
    if custom_instructions:
        user_prompt = f"""Based on this conversation, please answer the following question or request:

**Question/Request:** {custom_instructions}

Conversation:
{{conversation}}

Provide a clear, direct answer based only on the information in the conversation. If the answer isn't in the conversation, say so.

Respond in JSON format:
{{{{
    "summary": "your answer here",
    "key_points": ["supporting detail 1", "supporting detail 2", ...]
}}}}"""
    else:
        # Default summary format (optimized for speed)
        user_prompt = """Analyze this conversation and provide a concise summary.

Conversation:
{conversation}

Respond in JSON format with:
1. A brief summary (1-2 sentences max)
2. Top 3-5 key points only (short phrases, not full sentences)

{{
    "summary": "brief summary here",
    "key_points": ["key point 1", "key point 2", "key point 3"]
}}

Keep it SHORT and FAST."""
    
    # Create prompt template
    system_message = "You are an expert at analyzing team conversations for remote professionals. You provide clear, concise answers based on conversation context."
    prompt = ChatPromptTemplate.from_messages([
        ("system", system_message),
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
    # Simplified format (removed MSG_ID for speed - saves ~1000 input tokens!)
    conversation_text = "\n".join([
        f"[{msg.created_at.strftime('%H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # Create prompt template (ultra-simplified for speed)
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You extract action items from conversations. Be concise."),
        ("user", """Find action items in this conversation.

{conversation}

Extract only:
- task (short description)
- assigned_to (name or null)

JSON format:
{{
    "action_items": [
        {{"task": "...", "assigned_to": "..."}}
    ]
}}

Keep it SHORT.""")
    ])
    
    # Create chain (reuse main LLM with max_tokens=300 for speed)
    parser = JsonOutputParser()
    chain = prompt | llm | parser  # Uses global llm with max_tokens=300
    
    # Invoke chain
    result = await chain.ainvoke({"conversation": conversation_text})
    
    # Convert to ActionItem models
    action_items = []
    for item in result.get("action_items", []):
        action_items.append(ActionItem(
            task=item["task"],
            assigned_to=item.get("assigned_to"),
            deadline=None,  # Removed for speed
            priority=None,  # Removed for speed
            mentioned_in_message_id=None,  # Removed for speed
            context=None  # Removed for speed
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
    from services.rag_service import semantic_search_with_rag
    
    # Perform semantic search (skip LLM reranking for speed)
    results = await semantic_search_with_rag(query, messages, max_results)
    
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

