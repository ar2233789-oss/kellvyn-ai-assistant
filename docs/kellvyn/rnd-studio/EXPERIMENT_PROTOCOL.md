# Protocol for Kellvyn brain experiments

Each experiment must be falsifiable and reproducible, not merely “make it smarter/faster”.

Record:
- unique ID, UTC timestamp, task/worktree branch, baseline commit and candidate commit;
- hypothesis and reason;
- exact changed files and reviewable diff;
- model and tokenizer versions, checkpoint SHA-256 and inference settings;
- authorized dataset manifest, rights evidence, content hashes, deduplication and TRAIN/VALIDATION/TEST splits;
- random seed, optimizer, learning rate, batch size, context, steps and other training settings;
- device/OS/build, thermal starting condition and power mode for device tests;
- baseline/candidate run IDs, logs, metrics, errors and artifact hashes;
- predeclared acceptance and regression thresholds;
- outcome: PASSED, FAILED, INCONCLUSIVE or NOT_RUN.

Rules:
1. Change one main variable per experiment unless its interaction is the hypothesis.
2. Keep the baseline under the same environment and protocol.
3. Repeat noisy performance runs and report spread/median, not only the best.
4. Compare numerical code to a small deterministic reference.
5. For language quality, use held-out Portuguese tasks and independent grading; loss alone is insufficient.
6. For KV cache, compare every decode step to full-prefix output before measuring speed.
7. A speedup must preserve quality and report resource use.
8. Never promote weights or runtime paths automatically; preserve rollback.
9. Missing execution evidence is NOT_RUN or INCONCLUSIVE, never PASSED.
