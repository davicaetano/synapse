"""
RAG (Retrieval-Augmented Generation) service for semantic search
Uses LangChain + ChromaDB + OpenAI Embeddings
"""

import os
from typing import List, Dict, Any
from langchain_openai import OpenAIEmbeddings
from langchain_community.vectorstores import Chroma
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.docstore.document import Document
from models.schemas import Message

# Initialize embeddings
embeddings = OpenAIEmbeddings(
    model="text-embedding-3-small",
    api_key=os.getenv("OPENAI_API_KEY")
)

async def semantic_search_with_rag(
    query: str,
    messages: List[Message],
    max_results: int = 10
) -> List[Dict[str, Any]]:
    """
    Perform semantic search using RAG pipeline
    
    Steps:
    1. Convert messages to documents
    2. Create embeddings
    3. Store in vector database (ChromaDB)
    4. Query with semantic similarity
    5. Return top results with scores
    """
    
    # Step 1: Convert messages to LangChain documents
    documents = []
    for msg in messages:
        doc = Document(
            page_content=msg.text,
            metadata={
                "message_id": msg.id,
                "sender_name": msg.sender_name or "Unknown",
                "sender_id": msg.sender_id,
                "timestamp": msg.created_at.isoformat(),
                "conversation_id": msg.conversation_id
            }
        )
        documents.append(doc)
    
    # Step 2 & 3: Create vector store with embeddings
    # Using in-memory Chroma for fast queries (no persistence needed)
    vectorstore = Chroma.from_documents(
        documents=documents,
        embedding=embeddings,
        collection_name="messages_temp"
    )
    
    # Step 4: Perform similarity search
    results = vectorstore.similarity_search_with_score(
        query=query,
        k=max_results
    )
    
    # Step 5: Format results
    formatted_results = []
    for doc, score in results:
        formatted_results.append({
            "message_id": doc.metadata["message_id"],
            "relevance_score": float(1 - score),  # Convert distance to similarity (0-1)
            "explanation": f"Semantic similarity match"
        })
    
    return formatted_results


async def create_conversation_index(messages: List[Message]) -> Chroma:
    """
    Create a persistent vector index for a conversation
    Can be used for fast repeated searches
    """
    documents = []
    for msg in messages:
        doc = Document(
            page_content=msg.text,
            metadata={
                "message_id": msg.id,
                "sender_name": msg.sender_name or "Unknown",
                "timestamp": msg.created_at.isoformat()
            }
        )
        documents.append(doc)
    
    # Create persistent vector store
    vectorstore = Chroma.from_documents(
        documents=documents,
        embedding=embeddings,
        collection_name=f"conv_{messages[0].conversation_id}",
        persist_directory="./chroma_db"
    )
    
    return vectorstore


async def hybrid_search(
    query: str,
    messages: List[Message],
    max_results: int = 10
) -> List[Dict[str, Any]]:
    """
    Hybrid search combining:
    1. Vector similarity (semantic)
    2. LLM reranking for relevance
    """
    from langchain_openai import ChatOpenAI
    from langchain.prompts import ChatPromptTemplate
    from langchain.output_parsers import JsonOutputParser
    
    # First, get semantic matches
    semantic_results = await semantic_search_with_rag(query, messages, max_results * 2)
    
    # Get the messages for reranking
    candidate_messages = []
    for result in semantic_results:
        msg = next((m for m in messages if m.id == result["message_id"]), None)
        if msg:
            candidate_messages.append({
                "id": msg.id,
                "text": msg.text,
                "sender": msg.sender_name
            })
    
    # Rerank with LLM
    llm = ChatOpenAI(model="gpt-3.5-turbo", temperature=0.1)
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are a search relevance expert."),
        ("user", """Query: "{query}"

Candidate messages:
{candidates}

Rank these messages by relevance to the query.
Return the top {max_results} with relevance scores (0.0 to 1.0).

JSON format:
{{
    "results": [
        {{
            "message_id": "id",
            "relevance_score": 0.95,
            "explanation": "why relevant"
        }}
    ]
}}""")
    ])
    
    parser = JsonOutputParser()
    chain = prompt | llm | parser
    
    result = await chain.ainvoke({
        "query": query,
        "candidates": candidate_messages,
        "max_results": max_results
    })
    
    return result.get("results", [])

