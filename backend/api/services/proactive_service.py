"""
Proactive Assistant - LangGraph Multi-Agent System
Uses StateGraph to orchestrate context detection and specialized suggestions
"""

import os
from typing import List, Dict, Any, TypedDict, Literal
from dotenv import load_dotenv
from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser
from models.schemas import Message
from datetime import datetime, timedelta

# Load environment variables
load_dotenv()

# Initialize LLM for agents
agent_llm = ChatOpenAI(
    model="gpt-3.5-turbo",
    temperature=0.3,
    max_tokens=400,
    api_key=os.getenv("OPENAI_API_KEY")
)

# Define agent state
class ProactiveState(TypedDict):
    """State that gets passed between agent steps"""
    messages: List[Message]
    conversation_id: str
    
    # Step 1: Context Detection
    should_act: bool
    context_type: str  # "cinema" | "restaurant" | "generic" | "none"
    confidence: float
    reason: str
    
    # Step 2: Specialized Agent
    suggestion_text: str
    
    current_step: int
    total_steps: int

# ============================================================
# STEP 1: CONTEXT DETECTOR AGENT (Gatekeeper)
# ============================================================

async def context_detector_step(state: ProactiveState) -> ProactiveState:
    """
    Analyzes conversation to decide IF we should act and WHAT context
    
    Returns:
    - should_act: True/False
    - context_type: cinema, restaurant, generic, none
    - confidence: 0.0-1.0
    """
    print(f"🤖 [PROACTIVE] Step {state['current_step']}/{state['total_steps']}: Context Detection...")
    
    messages = state['messages']
    
    # Check 1: Anti-spam (AI already suggested in last 10 messages?)
    last_10 = messages[-10:] if len(messages) >= 10 else messages
    ai_messages = [msg for msg in last_10 if msg.sender_id == "synapse-bot-system"]
    
    if len(ai_messages) > 0:
        print(f"⏸️  [PROACTIVE] Anti-spam: AI already active in last 10 messages")
        state['should_act'] = False
        state['context_type'] = "none"
        state['confidence'] = 0.0
        state['reason'] = "anti_spam"
        return state
    
    # Check 2: Conversation active? (last message < 5 minutes)
    if len(messages) > 0:
        last_msg = messages[-1]
        time_since_last = datetime.now() - last_msg.created_at
        if time_since_last > timedelta(minutes=5):
            print(f"⏸️  [PROACTIVE] Conversation stale ({time_since_last.seconds}s old)")
            state['should_act'] = False
            state['context_type'] = "none"
            state['confidence'] = 0.0
            state['reason'] = "stale_conversation"
            return state
    
    # Check 3: Has actionable context? (LLM analysis)
    last_20 = messages[-20:] if len(messages) >= 20 else messages
    conversation_text = "\n".join([
        f"[{msg.created_at.strftime('%H:%M')}] {msg.sender_name}: {msg.text}"
        for msg in last_20
    ])
    
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are a context analyzer. Determine if conversation has actionable context for suggestions."),
        ("user", """Analyze this conversation:

{conversation}

Is there actionable context for helpful suggestions?

CONTEXTS:
- cinema: discussing movies, films, watching something, entertainment
- restaurant: discussing food, eating, restaurants, dining
- generic: vague planning ("let's meet", "what should we do") but no specific context

Return JSON:
{{
    "should_act": true/false,
    "context_type": "cinema" | "restaurant" | "generic" | "none",
    "confidence": 0.0-1.0,
    "reason": "brief explanation"
}}

BE CONSERVATIVE. Only suggest if conversation CLEARLY indicates a need.
""")
    ])
    
    parser = JsonOutputParser()
    chain = prompt | agent_llm | parser
    
    result = await chain.ainvoke({"conversation": conversation_text})
    
    state['should_act'] = result.get("should_act", False)
    state['context_type'] = result.get("context_type", "none")
    state['confidence'] = result.get("confidence", 0.0)
    state['reason'] = result.get("reason", "")
    state['current_step'] += 1
    
    print(f"✅ [PROACTIVE] Context: {state['context_type']} (confidence: {state['confidence']:.2f})")
    
    return state

# ============================================================
# STEP 2: SPECIALIZED AGENTS (Generators)
# ============================================================

async def cinema_agent_step(state: ProactiveState) -> ProactiveState:
    """Generate cinema/movie suggestions"""
    print(f"🤖 [PROACTIVE] Step {state['current_step']}/{state['total_steps']}: Cinema Agent...")
    
    # Mock movie suggestions (in production, call TMDb API)
    suggestion = """🎬 **Sugestões de Filmes**

Baseado na conversa, aqui estão filmes em cartaz:

1. **Dune: Part Two**
   Sci-fi épico • 2h46min • ⭐9.0/10
   
2. **Oppenheimer**
   Drama biográfico • 3h00min • ⭐8.5/10
   
3. **Poor Things**
   Comédia dramática • 2h21min • ⭐8.2/10

📍 Cinemas próximos: Cinemark, UCI, Kinoplex"""
    
    state['suggestion_text'] = suggestion
    state['current_step'] += 1
    
    print(f"✅ [PROACTIVE] Generated cinema suggestions")
    
    return state

async def restaurant_agent_step(state: ProactiveState) -> ProactiveState:
    """Generate restaurant suggestions"""
    print(f"🤖 [PROACTIVE] Step {state['current_step']}/{state['total_steps']}: Restaurant Agent...")
    
    # Mock restaurant suggestions (in production, call Google Places API)
    suggestion = """🍽️ **Sugestões de Restaurantes**

Próximos e bem avaliados:

1. **Bella Italia**
   Italiano • ⭐4.8/5 • $$ • 800m
   
2. **Sushi House**
   Japonês • ⭐4.6/5 • $$$ • 1.2km
   
3. **BBQ Master**
   Churrascaria • ⭐4.7/5 • $$$ • 1.5km

💡 Dica: Bella Italia aceita reservas online!"""
    
    state['suggestion_text'] = suggestion
    state['current_step'] += 1
    
    print(f"✅ [PROACTIVE] Generated restaurant suggestions")
    
    return state

async def generic_agent_step(state: ProactiveState) -> ProactiveState:
    """Generate generic helpful suggestions"""
    print(f"🤖 [PROACTIVE] Step {state['current_step']}/{state['total_steps']}: Generic Agent...")
    
    suggestion = """💡 **Posso ajudar!**

Parece que vocês estão planejando algo. 

Posso sugerir:
- 🎬 Filmes e entretenimento
- 🍽️ Restaurantes e cafés
- 🎯 Atividades e lugares

Me avisem se precisarem de sugestões específicas!"""
    
    state['suggestion_text'] = suggestion
    state['current_step'] += 1
    
    print(f"✅ [PROACTIVE] Generated generic suggestions")
    
    return state

# ============================================================
# ROUTING LOGIC (Conditional Edges)
# ============================================================

def should_continue_after_detection(state: ProactiveState) -> str:
    """Decide if we should continue to specialized agent or stop"""
    if state['should_act']:
        return "route_to_specialist"
    else:
        return "end"

def route_to_specialist(state: ProactiveState) -> str:
    """Route to appropriate specialist based on context_type"""
    context = state['context_type']
    
    if context == "cinema":
        return "cinema_agent"
    elif context == "restaurant":
        return "restaurant_agent"
    else:
        return "generic_agent"

# ============================================================
# MAIN PROACTIVE ASSISTANT WORKFLOW
# ============================================================

async def run_proactive_assistant(
    messages: List[Message],
    conversation_id: str
) -> Dict[str, Any]:
    """
    Multi-agent LangGraph workflow for proactive suggestions
    
    Flow:
    1. Context Detector → Decides IF and WHAT
    2. Router → Routes to specialized agent
    3. Specialist → Generates suggestions
    
    Returns:
    - suggestion_text: Formatted suggestion (or None if no action)
    - context_type: Type of suggestion made
    - confidence: How confident the system is
    """
    
    # Initialize state
    initial_state = ProactiveState(
        messages=messages,
        conversation_id=conversation_id,
        should_act=False,
        context_type="none",
        confidence=0.0,
        reason="",
        suggestion_text="",
        current_step=1,
        total_steps=2  # Detection + Generation
    )
    
    # Build LangGraph workflow
    workflow = StateGraph(ProactiveState)
    
    # Add nodes
    workflow.add_node("context_detector", context_detector_step)
    workflow.add_node("cinema_agent", cinema_agent_step)
    workflow.add_node("restaurant_agent", restaurant_agent_step)
    workflow.add_node("generic_agent", generic_agent_step)
    
    # Set entry point
    workflow.set_entry_point("context_detector")
    
    # Conditional routing after context detection
    workflow.add_conditional_edges(
        "context_detector",
        should_continue_after_detection,
        {
            "route_to_specialist": "context_detector",  # Needs second decision
            "end": END
        }
    )
    
    # Actually, we need a cleaner approach - let me fix this
    # After context_detector, route directly to specialist or END
    workflow.add_conditional_edges(
        "context_detector",
        lambda state: route_to_specialist(state) if state["should_act"] else END,
        {
            "cinema_agent": "cinema_agent",
            "restaurant_agent": "restaurant_agent",
            "generic_agent": "generic_agent",
            END: END
        }
    )
    
    # All specialists end the workflow
    workflow.add_edge("cinema_agent", END)
    workflow.add_edge("restaurant_agent", END)
    workflow.add_edge("generic_agent", END)
    
    # Compile graph
    app = workflow.compile()
    
    # Execute workflow
    final_state = await app.ainvoke(initial_state)
    
    # Return result
    return {
        "should_act": final_state['should_act'],
        "context_type": final_state['context_type'],
        "confidence": final_state['confidence'],
        "reason": final_state['reason'],
        "suggestion_text": final_state.get('suggestion_text', None)
    }

