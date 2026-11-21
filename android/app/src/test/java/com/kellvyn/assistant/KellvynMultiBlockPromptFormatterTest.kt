package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynMultiBlockPromptFormatterTest {
    @Test
    fun trainingAndLivePromptFormattingProduceTheSameInput() {
        val tool = object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "local_lookup",
                description = "Consulta um registro local.",
                requiredArguments = setOf("query"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "query",
                        type = KellvynToolArgumentType.TEXT,
                        required = true,
                        maxLength = 80
                    )
                )
            )
            override suspend fun execute(arguments: Map<String, String>) =
                KellvynToolResult(true, "ok")
        }
        val instruction = KellvynDecisionPromptContext.build(
            goal = "Pesquise o registro local KN."
        )
        val registry = KellvynToolRegistry(listOf(tool))
        val history = listOf(
            KellvynAgentMessage(
                role = KellvynMessageRole.SYSTEM,
                trust = KellvynTrustLevel.TRUSTED,
                source = "kellvyn-core",
                content = instruction
            ),
            KellvynAgentMessage(
                role = KellvynMessageRole.USER,
                trust = KellvynTrustLevel.USER_PROVIDED,
                source = "user",
                content = "Pesquise o registro local KN."
            )
        )
        val shortlist = registry.descriptionsForPrompt(history, maxTools = 6)

        val trainingPrompt = KellvynMultiBlockPromptFormatter.buildTrainingPrompt(
            userPrompt = "Pesquise o registro local KN.",
            availableTools = shortlist,
            systemInstruction = instruction
        )
        val livePrompt = KellvynMultiBlockPromptFormatter.build(
            history = history,
            availableTools = shortlist
        )

        assertEquals(livePrompt, trainingPrompt)
        assertTrue(trainingPrompt.contains("KELLVYN/1"))
        assertTrue(trainingPrompt.contains("local_lookup"))
        assertTrue(trainingPrompt.contains("DECISÃO KELLVYN:"))
    }

    @Test
    fun toolCatalogIsSortedAndArgumentsAreIncludedInPrompt() {
        val tools = listOf("z_tool", "a_tool").map { name ->
            object : KellvynAgentTool {
                override val spec = KellvynToolSpec(
                    name = name,
                    description = "Ferramenta $name."
                )
                override suspend fun execute(arguments: Map<String, String>) =
                    KellvynToolResult(true, "ok")
            }
        }
        val prompt = KellvynMultiBlockPromptFormatter.buildTrainingPrompt(
            userPrompt = "Inspecione as ferramentas.",
            availableTools = tools.map { it.spec }
        )

        assertTrue(prompt.indexOf("a_tool") < prompt.indexOf("z_tool"))
    }
}
