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
    dev_summary: bool = Field(False, description="Include dev info (processing time, model version)")

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
    custom_instructions: Optional[str] = Field(None, description="Custom instructions")
    dev_summary: bool = Field(False, description="Include dev info (processing time, model version)")

class ActionItem(BaseModel):
    task: str
    assigned_to: Optional[str] = None
    deadline: Optional[str] = None  # Not extracted anymore (speed optimization)
    priority: Optional[str] = None  # Not extracted anymore (speed optimization)
    mentioned_in_message_id: Optional[str] = None  # Not extracted anymore (speed optimization)
    context: Optional[str] = None  # Not extracted anymore (speed optimization)

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
    dev_summary: bool = Field(False, description="Include dev info (processing time, model version)")

# ============================================================
# DECISION TRACKING
# ============================================================

class DecisionTrackingRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")
    start_date: Optional[str] = Field(None, description="ISO format start date")
    end_date: Optional[str] = Field(None, description="ISO format end date")
    dev_summary: bool = Field(False, description="Include dev info (processing time, model version)")

class Decision(BaseModel):
    decision: str
    decided_by: List[str]  # User names who agreed
    timestamp: str
    confidence: float  # 0.0 to 1.0
    context: str  # Surrounding messages
    message_ids: List[str]  # Messages that led to decision

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
# PROACTIVE ASSISTANT (Advanced Multi-Agent)
# ============================================================

class ProactiveRequest(BaseModel):
    conversation_id: str = Field(..., description="Firestore conversation ID")

class ProactiveResponse(BaseModel):
    success: bool
    should_act: bool
    context_type: Optional[str] = None  # "cinema" | "restaurant" | "generic" | "none"
    confidence: Optional[float] = None  # 0.0 to 1.0
    message_id: Optional[str] = None  # AI message ID if created
    reason: Optional[str] = None  # Why no action (anti_spam, stale_conversation, etc.)
    processing_time_ms: Optional[int] = None

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

