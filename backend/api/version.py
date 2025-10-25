"""
API Version Management
Increment this version number before each deployment to track changes
"""

# Semantic versioning: MAJOR.MINOR.PATCH
API_VERSION = "1.2.3"

# Version history:
# 1.0.0 - Initial release with GPT-4
# 1.1.0 - Added all 5 AI features (summarization, action items, search, priority, decisions)
# 1.2.0 - Performance optimization: GPT-4 -> GPT-3.5-turbo, 50 msg limit, type filtering
# 1.2.1 - RAG implementation: Intelligent message deduplication with clustering (too heavy for Render)
# 1.2.2 - RAG disabled: scikit-learn too heavy for Render free tier (back to direct GPT)
# 1.2.3 - Speed optimization: Custom Q&A support + shorter summaries (1-2s response time)

