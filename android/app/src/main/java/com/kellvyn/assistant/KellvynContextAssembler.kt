package com.kellvyn.assistant

/**
 * Deterministic context-window budgeter.
 *
 * This is a character budget, not a claim about exact model tokens. The selected model
 * tokenizer will replace this estimate when a real inference backend is integrated.
 */
internal object KellvynContextAssembler {
    sealed class Result {
        data class Ready(
            val messages: List<KellvynAgentMessage>,
            val estimatedChars: Int,
            val droppedMessages: Int
        ) : Result()

        data class TooLarge(val requiredChars: Int, val budgetChars: Int) : Result()
    }

    fun fit(
        messages: List<KellvynAgentMessage>,
        maxChars: Int
    ): Result {
        require(maxChars in MIN_BUDGET_CHARS..MAX_BUDGET_CHARS)
        if (messages.isEmpty()) return Result.Ready(emptyList(), 0, 0)

        val pinned = messages.indices.filter { messages[it].role == KellvynMessageRole.SYSTEM }
            .toMutableSet()
        val latestUser = messages.indexOfLast { it.role == KellvynMessageRole.USER }
        if (latestUser >= 0) pinned += latestUser

        val requiredChars = pinned.sumOf { cost(messages[it]) }
        if (requiredChars > maxChars) {
            return Result.TooLarge(requiredChars, maxChars)
        }

        var remaining = maxChars - requiredChars
        val retained = pinned.toMutableSet()

        for (index in messages.indices.reversed()) {
            if (index in retained) continue
            val itemCost = cost(messages[index])
            if (itemCost <= remaining) {
                retained += index
                remaining -= itemCost
            }
        }

        val selected = retained.sorted().map { messages[it] }
        val used = selected.sumOf(::cost)
        return Result.Ready(
            messages = selected,
            estimatedChars = used,
            droppedMessages = messages.size - selected.size
        )
    }

    private fun cost(message: KellvynAgentMessage): Int =
        HEADER_OVERHEAD + message.source.length + message.content.length

    private const val HEADER_OVERHEAD = 32
    private const val MIN_BUDGET_CHARS = 256
    private const val MAX_BUDGET_CHARS = 200_000
}
