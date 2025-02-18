package com.bulifier.core.agents

import com.bulifier.core.db.HistoryItem

val AGENTS_MAP = mutableMapOf(
    "SIMPLE_AGENT" to SimpleAgent::class.java
)

interface Agent {
    suspend fun process(historyItem: HistoryItem)
}