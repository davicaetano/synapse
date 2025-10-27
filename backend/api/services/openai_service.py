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
    OPTIMIZED: Returns message content directly (not just IDs)
    """
    # Include message content in conversation text
    conversation_text = "\n".join([
        f"[{msg.created_at.strftime('%H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # OPTIMIZED prompt - returns message text directly
    prompt = ChatPromptTemplate.from_messages([
        ("system", "Identify urgent messages. BE BRIEF."),
        ("user", """Find urgent messages:
- "ASAP", "urgent", "deadline", "blocker"
- Direct questions needing quick answers
- Critical decisions

{conversation}

Extract TOP 5 ONLY. For each:
- message_text (quoted, max 100 chars)
- sender_name
- urgency_level (urgent/high/medium)
- reason (1-2 words)

JSON:
{{
    "priority_messages": [
        {{
            "message_text": "We need approval by EOD",
            "sender_name": "Sarah",
            "urgency_level": "urgent",
            "reason": "deadline"
        }}
    ]
}}""")
    ])
    
    # FAST limits (optimized for speed)
    llm_low_temp = ChatOpenAI(
        model="gpt-3.5-turbo", 
        temperature=0.1,
        max_tokens=250  # Reduced for faster response
    )
    parser = JsonOutputParser()
    chain = prompt | llm_low_temp | parser
    
    result = await chain.ainvoke({"conversation": conversation_text})
    return result.get("priority_messages", [])

# ============================================================
# DECISION TRACKING
# ============================================================

async def track_decisions(messages: List[Message]) -> List[Decision]:
    """
    Identify decisions made in conversation using LangChain
    ULTRA OPTIMIZED for speed: ~2-3s response time
    """
    # Simplified format - no MSG_ID (saves tokens)
    conversation_text = "\n".join([
        f"[{msg.created_at.strftime('%H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in messages
    ])
    
    # ULTRA simplified prompt (minimal instructions)
    prompt = ChatPromptTemplate.from_messages([
        ("system", "Extract decisions from conversation. BE BRIEF."),
        ("user", """Find decisions:
- "we decided", "let's go with", "agreed", "approved"

{conversation}

Extract TOP 3 ONLY:
- decision (1 sentence)
- decided_by (names)
- timestamp (time)

JSON:
{{
    "decisions": [
        {{
            "decision": "Use PostgreSQL",
            "decided_by": ["Sarah", "Alex"],
            "timestamp": "14:30"
        }}
    ]
}}""")
    ])
    
    # AGGRESSIVE limits for speed
    llm_low_temp = ChatOpenAI(
        model="gpt-3.5-turbo", 
        temperature=0.1,  # Lower = faster, more deterministic
        max_tokens=300    # Very limited output
    )
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
            confidence=0.85,  # Fixed confidence
            context="",  # No rationale
            message_ids=[]
        ))
    
    return decisions

