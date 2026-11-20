<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->

- Keep native inference lifecycle and model-download validation in the Android brain layer, separate from MainActivity's approved layout, so brain corrections cannot redesign the interface.
- Pin the native model to an immutable upstream revision and validate exact size, SHA-256 and resumed ranges; keep the pure download policy covered by Android unit tests before APK packaging.


## KN — obrigatório para qualquer tarefa no cérebro neural

A prioridade de engenharia é evoluir o modelo próprio KN-003 para KN-004 sem trocar a arquitetura de inteligência única.

Antes de alterar qualquer arquivo neural, leia integralmente:
@./docs/kellvyn/KN_ANDROID_STUDIO_IMPLEMENTATION_BRIEF_2026-11-20.md
@./docs/kellvyn/KN_UNIFIED_BRAIN_RESEARCH_2026-11-20.md

Regras vinculantes:
- Trabalhe exclusivamente no núcleo neural KN quando a tarefa for sobre o cérebro.
- O KN deve ser uma única rede Transformer decoder-only conectada de ponta a ponta. Todas as camadas pertencem ao mesmo modelo e à mesma retropropagação; não crie modelos decisórios independentes, especialistas, coordenadores ou arquiteturas multiagente.
- Não importe pesos, checkpoints, adapters ou LoRAs de modelos externos. Não use API, tokens pagos, cloud ou serviço remoto como dependência do cérebro.
- Não altere layout, UI, navegação, animações, temas ou componentes visuais Lovable.
- Preserve os checkpoints legados KN-003 e implemente versionamento explícito para novas arquiteturas.
- Não habilite o runtime conversacional principal antes de testes matemáticos, avaliação separada em validation/test e validação Android.
- Não declare build, teste, APK ou benchmark aprovado sem executar e observar o resultado real.
- Use o estimador de capacidade existente; qualquer expansão precisa incluir estimativas e medições reais de memória/desempenho.
- Prefira mudanças pequenas, isoladas, testáveis e reversíveis. Não enfraqueça ou remova testes para obter build verde.
- Ao concluir cada tarefa, informe arquivos alterados, commits, comandos realmente executados, resultados e bloqueios restantes.

## Quando usar Android Studio Agent Mode

Ao receber uma tarefa sobre KN, execute código no projeto aberto, não fique apenas escrevendo planos. Primeiro leia as duas especificações acima e os arquivos relacionados; depois implemente a maior etapa verificável, execute testes e build quando o ambiente permitir, revise os diffs e informe claramente o que não foi possível validar.
