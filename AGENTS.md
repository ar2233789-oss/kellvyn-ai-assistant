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

- Keep the native KN inference lifecycle separate from MainActivity's approved layout; intelligence changes must not redesign the interface.
- The only conversational brain is the original, centrally controlled KN model. Never download, load, bundle, import, fine-tune, or fall back to third-party language-model weights, checkpoints, adapters, or hosted inference.
- Do not restore or reintroduce the deleted Gemma/LiteRT web preview runtime. The Lovable web preview must state that KN inference is unavailable until the native KN runtime passes its gates; do not silently substitute another model.
- Preserve the Android Studio agent's existing work. Before changing neural/runtime/pipeline/store/test files, inspect the current branch HEAD, diff, and hashes; do not overwrite unreviewed changes.
- Preserve the approved MainActivity/layout, menus, animations, effects and interface-lock files.
