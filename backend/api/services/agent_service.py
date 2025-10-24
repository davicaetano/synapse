"""
Advanced AI Agent - Meeting Minutes Generator using LangGraph
Multi-step agent that autonomously analyzes conversations and generates comprehensive reports

Uses LangGraph for:
- State management
- Multi-step workflow
- Tool calling
- Agent orchestration
"""

import os
from typing import List, Dict, Any, TypedDict, Annotated
from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain.output_parsers import JsonOutputParser
from models.schemas import Message, ActionItem, Decision
from services import openai_service

# Initialize LLM for agent
agent_llm = ChatOpenAI(
    model="gpt-4-turbo-preview",
    temperature=0.3,
    api_key=os.getenv("OPENAI_API_KEY")
)

# Define agent state
class AgentState(TypedDict):
    """State that gets passed between agent steps"""
    messages: List[Message]
    conversation_id: str
    title: str
    participants: List[str]
    summary: str
    key_points: List[str]
    action_items: List[Dict]
    decisions: List[Dict]
    next_steps: List[str]
    formatted_document: str
    current_step: int
    total_steps: int

# ============================================================
# LANGGRAPH AGENT NODE FUNCTIONS
# ============================================================

async def step_1_analyze_context(state: AgentState) -> AgentState:
    """Step 1: Analyze conversation context"""
    print(f"🤖 Agent Step {state['current_step']}/{state['total_steps']}: Analyzing conversation context...")
    
    # Build metadata
    date_range = f"{state['messages'][0].created_at.strftime('%B %d, %Y')} - {state['messages'][-1].created_at.strftime('%B %d, %Y')}"
    
    state['current_step'] += 1
    return state

async def step_2_generate_summary(state: AgentState) -> AgentState:
    """Step 2: Generate executive summary"""
    print(f"🤖 Agent Step {state['current_step']}/{state['total_steps']}: Generating executive summary...")
    
    # Use openai_service to generate summary
    summary_result = await openai_service.summarize_thread(state['messages'])
    
    state['summary'] = summary_result['summary']
    state['key_points'] = summary_result['key_points']
    state['current_step'] += 1
    return state

async def step_3_extract_action_items(state: AgentState) -> AgentState:
    """Step 3: Extract action items"""
    print(f"🤖 Agent Step {state['current_step']}/{state['total_steps']}: Extracting action items...")
    
    # Use openai_service to extract action items
    action_items = await openai_service.extract_action_items(state['messages'])
    
    state['action_items'] = [item.dict() for item in action_items]
    state['current_step'] += 1
    return state

async def step_4_track_decisions(state: AgentState) -> AgentState:
    """Step 4: Track decisions"""
    print(f"🤖 Agent Step {state['current_step']}/{state['total_steps']}: Tracking decisions...")
    
    # Use openai_service to track decisions
    decisions = await openai_service.track_decisions(state['messages'])
    
    state['decisions'] = [dec.dict() for dec in decisions]
    state['current_step'] += 1
    return state

async def step_5_determine_next_steps(state: AgentState) -> AgentState:
    """Step 5: Determine next steps"""
    print(f"🤖 Agent Step {state['current_step']}/{state['total_steps']}: Determining next steps...")
    
    # Use LLM to generate next steps based on all previous analysis
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are an expert project manager analyzing a team conversation."),
        ("user", """Based on this analysis, generate 3-5 clear NEXT STEPS that the team should take.

Summary:
{summary}

Action Items:
{action_items}

Decisions Made:
{decisions}

These should be:
- Actionable and specific
- Forward-looking (what happens next)
- Prioritized by importance
- Different from the action items (higher level)

Respond in JSON format:
{{
    "next_steps": ["step 1", "step 2", ...]
}}""")
    ])
    
    parser = JsonOutputParser()
    chain = prompt | agent_llm | parser
    
    result = await chain.ainvoke({
        "summary": state['summary'],
        "action_items": state['action_items'],
        "decisions": state['decisions']
    })
    
    state['next_steps'] = result.get("next_steps", [])
    state['current_step'] += 1
    return state

async def step_6_format_document(state: AgentState) -> AgentState:
    """Step 6: Format final document"""
    print(f"🤖 Agent Step {state['current_step']}/{state['total_steps']}: Formatting final document...")
    
    date_range = f"{state['messages'][0].created_at.strftime('%B %d, %Y')} - {state['messages'][-1].created_at.strftime('%B %d, %Y')}"
    
    # Format markdown document
    doc = f"""# {state['title']}

**Date:** {date_range}  
**Participants:** {', '.join(state['participants'])}

---

## Executive Summary

{state['summary']}

---

## Key Discussion Points

{chr(10).join([f'- {point}' for point in state['key_points']])}

---

## Decisions Made

{chr(10).join([f'- **{dec["decision"]}**  \n  Decided by: {", ".join(dec["decided_by"])} (Confidence: {int(dec["confidence"] * 100)}%)' for dec in state['decisions']]) if state['decisions'] else '_No formal decisions recorded_'}

---

## Action Items

{chr(10).join([f'- [ ] **{item["task"]}**  \n  Assigned to: {item.get("assigned_to") or "Unassigned"}  \n  Deadline: {item.get("deadline") or "TBD"}  \n  Priority: {item["priority"].upper()}' for item in state['action_items']]) if state['action_items'] else '_No action items identified_'}

---

## Next Steps

{chr(10).join([f'{i+1}. {step}' for i, step in enumerate(state['next_steps'])])}

---

_Generated by Synapse AI - Meeting Minutes Agent (LangGraph)_
"""
    
    state['formatted_document'] = doc
    state['current_step'] += 1
    
    print(f"✅ Agent complete! Generated {len(doc)} character document")
    
    return state

# ============================================================
# MAIN AGENT FUNCTION WITH LANGGRAPH
# ============================================================

async def generate_meeting_minutes(
    messages: List[Message],
    conversation_id: str,
    title: str,
    participants: List[Dict[str, str]]
) -> Dict[str, Any]:
    """
    Multi-step LangGraph agent that generates comprehensive meeting minutes
    
    Uses StateGraph for:
    - State management across steps
    - Autonomous execution
    - Error recovery
    - Observability
    """
    
    # Initialize state
    initial_state = AgentState(
        messages=messages,
        conversation_id=conversation_id,
        title=title,
        participants=[p['name'] for p in participants],
        summary="",
        key_points=[],
        action_items=[],
        decisions=[],
        next_steps=[],
        formatted_document="",
        current_step=1,
        total_steps=6
    )
    
    # Build LangGraph workflow
    workflow = StateGraph(AgentState)
    
    # Add nodes (steps)
    workflow.add_node("analyze_context", step_1_analyze_context)
    workflow.add_node("generate_summary", step_2_generate_summary)
    workflow.add_node("extract_actions", step_3_extract_action_items)
    workflow.add_node("track_decisions", step_4_track_decisions)
    workflow.add_node("next_steps", step_5_determine_next_steps)
    workflow.add_node("format_doc", step_6_format_document)
    
    # Define workflow edges (step order)
    workflow.set_entry_point("analyze_context")
    workflow.add_edge("analyze_context", "generate_summary")
    workflow.add_edge("generate_summary", "extract_actions")
    workflow.add_edge("extract_actions", "track_decisions")
    workflow.add_edge("track_decisions", "next_steps")
    workflow.add_edge("next_steps", "format_doc")
    workflow.add_edge("format_doc", END)
    
    # Compile graph
    app = workflow.compile()
    
    # Execute agent workflow
    final_state = await app.ainvoke(initial_state)
    
    # Return result
    date_range = f"{messages[0].created_at.strftime('%B %d, %Y')} - {messages[-1].created_at.strftime('%B %d, %Y')}"
    
    return {
        "title": final_state['title'],
        "date_range": date_range,
        "summary": final_state['summary'],
        "participants": final_state['participants'],
        "key_points": final_state['key_points'],
        "action_items": final_state['action_items'],
        "decisions": final_state['decisions'],
        "next_steps": final_state['next_steps'],
        "formatted_document": final_state['formatted_document']
    }

# Old helper functions removed - now integrated into LangGraph workflow

