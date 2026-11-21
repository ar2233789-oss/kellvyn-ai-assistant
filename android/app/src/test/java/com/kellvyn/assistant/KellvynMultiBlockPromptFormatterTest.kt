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
        val shortlist = registry.descriptionsForPrompt(history, maxTools = KellvynDecisionPromptContext.MAX_TOOLS_IN_PROMPT)

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
    fun productionDefaultsKeepTrainingAndInferenceWithinOneSharedBudget() {
        val brainConfig = KellvynDecisionPromptContext.defaultConfig()
        val inferenceConfig = KellvynMultiBlockInferenceEngine.Config()

        assertEquals(KellvynDecisionPromptContext.MAX_TOOLS_IN_PROMPT, brainConfig.maxToolsInModelPrompt)
        assertEquals(brainConfig.maxToolsInModelPrompt, inferenceConfig.maxToolsInPrompt)
        assertEquals(KellvynDecisionPromptContext.MAX_MESSAGE_CHARS, inferenceConfig.maxMessageChars)
        assertEquals(KellvynDecisionPromptContext.MAX_TOOL_DESCRIPTION_CHARS, inferenceConfig.maxToolDescriptionChars)
        assertEquals(1_000, brainConfig.maxSkillContextChars)
    }

    @Test
    fun toolCatalogIsSortedAndArgumentsAreIncludedInPrompt() {
        val tools = listOf("z_tool", "a_tool").map { name ->
            object : KellvynAgentTool {
                override val spec = KellvynToolSpec(
                    name = name,
                    description = "Ferramenta $name.",
                    requiredArguments = if (name == "a_tool") setOf("segments") else emptySet(),
                    argumentSchemas = if (name == "a_tool") listOf(
                        KellvynToolArgumentSchema(
                            name = "segments",
                            type = KellvynToolArgumentType.INTEGER,
                            required = true,
                            maxLength = 3,
                            minInteger = 3,
                            maxInteger = 128
                        )
                    ) else emptyList(),
                    capabilities = if (name == "a_tool") {
                        setOf(KellvynStudioCapability.SCENE_3D)
                    } else {
                        emptySet()
                    }
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
        assertTrue(prompt.contains("cap=[scene_3d]"))
        assertTrue(prompt.contains("workload=LIGHT"))
        assertTrue(prompt.contains("segments:integer![max=3,min=3,maxInt=128]"))
    }
}
