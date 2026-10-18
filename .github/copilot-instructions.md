# Mandatory Copilot instructions — Kellvyn IA

These rules override any default redesign/refactor instincts. Read before changing code.

## 1. Freeze the original Lovable UI
The existing Lovable web app is the visual source of truth. Keep its layout 100% identical: structure, dimensions, spacing, positions, colors, icons, labels, buttons, menus, animation effects, timing, and behavior. Do not redesign, recreate, simplify, modernize, or replace it. Do not remove or add visual controls. Never edit `src/**`, CSS, stylesheets, or visual React components for a task focused on AI integration, unless the user explicitly authorizes a specific visual change. Treat the original animations (including rain/snow/lightning or any effects already present in the actual source) as untouchable. If an implementation risks affecting visual fidelity, stop and explain the conflict instead of deciding to change the interface.

For Android/native work, do not alter the Lovable web layout to fit Android. Make implementation changes isolated to `android/**` and the smallest necessary build files. Match the original as closely as the existing approved native/UI reference allows. Before finishing, list changed files and verify no web UI/style files were touched.

## 2. One AI only — no model/provider/mode selection
Kellvyn IA is exactly one assistant, not a collection of agents or selectable models. Never add or expose:
- "modo leve", "modo pesado", lightweight/heavy modes, or mode selectors;
- Qwen Code, Qwen Coder, or any Qwen option;
- model/core switching, variable cores, provider selection, model dropdowns, token meters, service tiers, or similar controls;
- alternate assistant identities or autonomous user-facing agents.

A runtime implementation detail is not a user-facing mode. Keep one coherent assistant and route the current task to its real capabilities without asking the user to choose a model/provider/mode.

## 3. The main chat is the only command center
All requests, task planning, project/app/bot creation, coding, file analysis, image/document analysis, web research, and tool invocation must originate from the main chat. The chat must understand the user's request and orchestrate the needed functions itself. Do not introduce independent agents, autonomous side-panels, separate command-center pages, or new menus to initiate those tasks. Existing utility buttons must retain their original appearance and may open their utility surface, but do not create a second AI or make the utility function an independent agent. The terminal is only to be opened or used when the user explicitly asks for terminal help in the main chat; never run commands automatically in the background. The code editor is only an editor, not another builder or agent.

## 4. Implement honestly
Only claim a capability is functional when its implementation is connected and tested. Never simulate an AI answer. Never claim web research, file/image/PDF analysis, bot operation, APK generation, terminal execution, or offline inference occurred unless the corresponding real implementation did it. No invented API keys, no mandatory paid provider, no exposed secrets, and no hidden cloud service for a supposedly local model. Ask before downloading large models and state the approximate download/storage requirements.

## 5. Workflow
Inspect repository state and history before making changes. Preserve existing valid work. Make narrowly scoped, reviewable changes. Do not touch visual files as a shortcut. Run build/tests and report actual results. Do not call a task complete while checks are failing, and do not publish an APK unless it actually built and passed package verification. Report limitations plainly.

When instructions conflict, prioritize: (1) preserve the original Lovable UI and animations; (2) single AI only; (3) main chat is the sole command center; (4) technical implementation only within the agreed scope.
