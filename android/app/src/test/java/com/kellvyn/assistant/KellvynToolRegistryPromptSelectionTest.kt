package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynToolRegistryPromptSelectionTest {
    @Test
    fun animationRequestGetsAnimationEngineAndAlwaysKeepsCapabilityInventory() {
        val registry = registry()
        val selected = registry.descriptionsForPrompt(
            history = listOf(user("Crie uma animação 2D com keyframes e movimento")),
            maxTools = 6
        ).map { it.name }.toSet()

        assertTrue("studio_capability_inventory" in selected)
        assertTrue("animation_render_spritesheet" in selected)
        assertFalse("image_resize" in selected)
        assertFalse("automation_schedule_resource_snapshot" in selected)
    }

    @Test
    fun academicResearchRequestSelectsScholarlySearchTool() {
        val selected = registry().descriptionsForPrompt(
            history = listOf(user("Pesquisar artigos científicos sobre modelos de linguagem")),
            maxTools = 6
        ).map { it.name }.toSet()

        assertTrue("studio_capability_inventory" in selected)
        assertTrue("academic_research" in selected)
        assertFalse("animation_render_spritesheet" in selected)
    }

    @Test
    fun imageRequestGetsRasterToolsWithoutUnrelatedModules() {
        val selected = registry().descriptionsForPrompt(
            history = listOf(user("Redimensionar foto PNG para 256 pixels")),
            maxTools = 6
        ).map { it.name }.toSet()

        assertTrue("studio_capability_inventory" in selected)
        assertTrue("image_inspect" in selected)
        assertTrue("image_resize" in selected)
        assertFalse("animation_render_spritesheet" in selected)
    }

    @Test
    fun imageIllustrationRequestGetsTheVectorArtworkRenderer() {
        val selected = registry().descriptionsForPrompt(
            history = listOf(user("Gere uma ilustração vetorial SVG com formas geométricas")),
            maxTools = 6
        ).map { it.name }.toSet()

        assertTrue("studio_capability_inventory" in selected)
        assertTrue("vector_render_artwork" in selected)
        assertFalse("animation_render_spritesheet" in selected)
    }

    @Test
    fun taskAndResourceRequestGetsBoundedAutomationAndDeviceTools() {
        val selected = registry().descriptionsForPrompt(
            history = listOf(user("Agendar tarefa para verificar RAM, bateria e temperatura")),
            maxTools = 6
        ).map { it.name }.toSet()

        assertTrue("studio_capability_inventory" in selected)
        assertTrue("automation_schedule_resource_snapshot" in selected)
        assertTrue("automation_task_status" in selected)
        assertTrue("device_resource_snapshot" in selected)
        assertFalse("image_resize" in selected)
    }

    @Test
    fun toolObservationCanRefineTheNextShortlist() {
        val selected = registry().descriptionsForPrompt(
            history = listOf(
                user("Inspecione o catálogo do laboratório."),
                KellvynAgentMessage(
                    role = KellvynMessageRole.TOOL,
                    trust = KellvynTrustLevel.UNTRUSTED,
                    source = "tool:studio_capability_inventory",
                    content = "RASTER_IMAGE_EDIT image_resize; ANIMATION animation_render_spritesheet"
                )
            ),
            maxTools = 5
        ).map { it.name }.toSet()

        assertTrue("studio_capability_inventory" in selected)
        assertTrue("image_resize" in selected)
        assertTrue("animation_render_spritesheet" in selected)
    }

    @Test
    fun shortlistNeverExceedsTheConfiguredBudget() {
        val selected = registry().descriptionsForPrompt(
            history = listOf(user("quero imagem animação projeto código automação")),
            maxTools = 2
        )

        assertEquals(2, selected.size)
        assertEquals("studio_capability_inventory", selected.first().name)
    }

    private fun registry(): KellvynToolRegistry = KellvynToolRegistry(
        listOf(
            tool(
                "studio_capability_inventory",
                "Lista as capacidades registradas do laboratório invisível.",
                setOf(KellvynStudioCapability.ARTIFACT_STORAGE)
            ),
            tool(
                "image_inspect",
                "Inspeciona dimensões e formato de uma imagem.",
                setOf(KellvynStudioCapability.VISUAL_ANALYSIS, KellvynStudioCapability.RASTER_IMAGE_EDIT)
            ),
            tool(
                "image_resize",
                "Redimensiona uma imagem e exporta PNG JPEG ou WebP.",
                setOf(KellvynStudioCapability.RASTER_IMAGE_EDIT)
            ),
            tool(
                "animation_render_spritesheet",
                "Renderiza uma animação 2D com keyframes e movimento em spritesheet.",
                setOf(KellvynStudioCapability.ANIMATION, KellvynStudioCapability.VISUAL_ANALYSIS)
            ),
            tool(
                "vector_render_artwork",
                "Cria ilustração vetorial SVG e prévia PNG a partir de formas geométricas.",
                setOf(KellvynStudioCapability.VECTOR_GRAPHICS, KellvynStudioCapability.ARTIFACT_STORAGE)
            ),
            tool(
                "automation_schedule_resource_snapshot",
                "Agenda leitura de recursos e temperatura do dispositivo.",
                setOf(KellvynStudioCapability.WORKFLOW_AUTOMATION)
            ),
            tool(
                "automation_task_status",
                "Consulta o estado de uma tarefa agendada.",
                setOf(KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS)
            ),
            tool(
                "device_resource_snapshot",
                "Consulta RAM bateria CPU e temperatura.",
                setOf(KellvynStudioCapability.DEVICE_DIAGNOSTICS)
            ),
            tool(
                "academic_research",
                "Pesquisa artigos científicos e trabalhos acadêmicos em OpenAlex e Crossref; retorna DOI, autores, ano e resumo.",
                setOf(KellvynStudioCapability.WEB_RESEARCH)
            ),
            tool(
                "code_find_symbols",
                "Localiza símbolos em arquivos Kotlin Java e código do projeto.",
                setOf(KellvynStudioCapability.CODE_NAVIGATION)
            )
        )
    )

    private fun tool(
        name: String,
        description: String,
        capabilities: Set<KellvynStudioCapability>
    ) = object : KellvynAgentTool {
        override val spec = KellvynToolSpec(
            name = name,
            description = description,
            capabilities = capabilities
        )
        override suspend fun execute(arguments: Map<String, String>) =
            KellvynToolResult(true, "ok")
    }

    private fun user(content: String) = KellvynAgentMessage(
        role = KellvynMessageRole.USER,
        trust = KellvynTrustLevel.USER_PROVIDED,
        source = "user",
        content = content
    )
}
