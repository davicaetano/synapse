"""
Pydantic models for request/response schemas
"""

from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime

# ============================================================
# SUMMARIZATION
# ============================================================

class SummarizeRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")
    start_date: Optional[str] = Field(None, description="ISO format start date")
    end_date: Optional[str] = Field(None, description="ISO format end date")
    max_messages: int = Field(1000, description="Max messages to analyze")
    custom_instructions: Optional[str] = Field(None, description="Custom instructions for focused summary")

class SummaryResponse(BaseModel):
    conversation_id: str
    summary: str
    key_points: List[str]
    participant_count: int
    message_count: int
    date_range: str
    processing_time_ms: int

# ============================================================
# ACTION ITEMS
# ============================================================

class ActionItemsRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")
    start_date: Optional[str] = Field(None, description="ISO format start date")
    end_date: Optional[str] = Field(None, description="ISO format end date")

class ActionItem(BaseModel):
    task: str
    assigned_to: Optional[str] = None
    deadline: Optional[str] = None
    priority: str = "medium"  # low, medium, high
    mentioned_in_message_id: str
    context: str  # Surrounding text

class ActionItemsResponse(BaseModel):
    conversation_id: str
    action_items: List[ActionItem]
    total_count: int
    processing_time_ms: int

# ============================================================
# SMART SEARCH
# ============================================================

class SearchRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")
    query: str = Field(..., description="Natural language search query")
    max_results: int = Field(10, description="Max results to return")

class SearchResult(BaseModel):
    message_id: str
    text: str
    sender_name: str
    timestamp: str
    relevance_score: float
    context_before: Optional[str] = None
    context_after: Optional[str] = None

class SearchResponse(BaseModel):
    query: str
    results: List[SearchResult]
    total_found: int
    processing_time_ms: int

# ============================================================
# PRIORITY DETECTION
# ============================================================

class PriorityDetectionRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")
    message_id: Optional[str] = Field(None, description="Specific message to analyze")

class PriorityMessage(BaseModel):
    message_id: str
    text: str
    sender_name: str
    priority_score: float  # 0.0 to 1.0
    urgency_level: str  # low, medium, high, urgent
    reasons: List[str]  # Why it's prioritized
    timestamp: str

class PriorityDetectionResponse(BaseModel):
    conversation_id: str
    priority_messages: List[PriorityMessage]
    total_analyzed: int
    processing_time_ms: int

# ============================================================
# DECISION TRACKING
# ============================================================

class DecisionTrackingRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")
    start_date: Optional[str] = Field(None, description="ISO format start date")
    end_date: Optional[str] = Field(None, description="ISO format end date")

class Decision(BaseModel):
    decision: str
    decided_by: List[str]  # User names who agreed
    timestamp: str
    confidence: float  # 0.0 to 1.0
    context: str  # Surrounding messages
    message_ids: List[str]  # Messages that led to decision

class DecisionTrackingResponse(BaseModel):
    conversation_id: str
    decisions: List[Decision]
    total_count: int
    processing_time_ms: int

# ============================================================
# ADVANCED AGENT (Meeting Minutes)
# ============================================================

class MeetingMinutesRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")
    start_date: Optional[str] = Field(None, description="ISO format start date")
    end_date: Optional[str] = Field(None, description="ISO format end date")
    title: Optional[str] = Field(None, description="Meeting title")

class MeetingMinutesResponse(BaseModel):
    conversation_id: str
    title: str
    date_range: str
    summary: str
    participants: List[str]
    key_points: List[str]
    action_items: List[ActionItem]
    decisions: List[Decision]
    next_steps: List[str]
    formatted_document: str  # Markdown formatted
    processing_time_ms: int

# ============================================================
# INTERNAL MODELS
# ============================================================

class Message(BaseModel):
    """Internal message model from Firestore"""
    id: str
    text: str
    sender_id: str
    sender_name: Optional[str] = None
    created_at: datetime
    conversation_id: str

