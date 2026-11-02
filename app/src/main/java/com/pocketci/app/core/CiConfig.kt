// ============================================================================
// PocketCI — CiConfig.kt
// ----------------------------------------------------------------------------
// The data model of a pipeline definition. This is what `pocketci.yml`
// (living in the repo being built) becomes after PipelineParser (Phase 33)
// reads it. Example YAML → object mapping:
//
//     pipeline:                                    CiConfig(
//       - checkout                                   StepDef("checkout"),
//       - build: { task: assembleDebug }             StepDef("build",
//       - test:  { task: testDebugUnitTest }                mapOf("task" to
//       - sign                                       "assembleDebug")),
//       - install                                    ...
//       - report                                     )
//
// Keep this file DUMB. It's a bag of data. All behavior (defaults,
// validation, execution) lives elsewhere — in the steps themselves
// (Phases 35-40) and in StepExecutor (Phase 34). A data class that grows
// methods is a class that grew a second job.
// ============================================================================

package com.pocketci.app.core

/**
 * A parsed pipeline: an ORDERED list of steps.
 *
 * Ordered, not a Set, not a Map — a pipeline is a sequence; "install"
 * before "build" is nonsense and we must preserve whatever order the
 * YAML declared (including duplicates, weird as they may be — if a user
 * writes `- build` twice, they get two builds; that's a their-problem,
 * and the logs will make it obvious).
 *
 * DEFAULT = empty pipeline. Deliberate: an empty YAML parses to an
 * empty list, which StepExecutor treats as "instant success". Why not
 * an error? Because a repo with a `pocketci.yml` containing literally
 * nothing is a repo that opted into PocketCI with nothing to do —
 * marking the commit green with zero work is the honest outcome.
 */
data class CiConfig(
    val pipeline: List<StepDef> = emptyList(),
) {

    /**
     * ONE step: a name + its arguments.
     *
     * [name] is the lookup key into StepExecutor's registry — the string
     * must EXACTLY match a Step.name (e.g. CheckoutStep.name == "checkout").
     * A typo in the YAML ("chekout") surfaces as StepExecutor's
     * "unknown step 'chekout'" Err — a human-readable failure, not a
     * ClassNotFoundException stack trace. That error quality difference
     * is this whole architecture in miniature.
     *
     * [args] is a flat String→String map. Deliberately NOT a typed model
     * per step:
     *   - Typed models per step = 6 extra classes + a polymorphic YAML
     *     deserializer, to save `args["task"] ?: "assembleDebug"`.
     *   - Flat maps are what snakeyaml gives us naturally, they degrade
     *     gracefully (unknown keys ignored), and steps already own their
     *     default-value logic (BuildStep picks "assembleDebug").
     *   - Trade-off accepted: typos in arg KEYS (tasK) silently fall back
     *     to defaults. Mitigation, if it becomes a real problem: log the
     *     raw args map at step start (RunContext.log already does this
     *     via StepExecutor) — visible in the run log.
     */
    data class StepDef(
        val name: String,
        val args: Map<String, String> = emptyMap(),
    )
}

// ----------------------------------------------------------------------------
// DESIGN NOTE — why CiConfig doesn't validate itself:
//
// Option A (rejected): CiConfig.init { require(name.isNotBlank()) ... }
//   - Fails at PARSE time with exceptions → breaks the CiResult error
//     model (Phase 16) and forces try/catch around construction.
//
// Option B (chosen): validation at EXECUTION time, where it returns Err.
//   - StepExecutor receives StepDef, looks up the step; lookup failure IS
//     the validation for names. Arity checks (did build get its `task`?)
//     happen inside the step with a clear Err message.
//   - Everything stays in the Result universe; nothing throws.
//
// This mirrors how real CI systems behave: GitHub Actions doesn't reject
// your YAML at push time — it runs and fails the step with a red log.
// Users debug CI by READING LOGS. We optimize for that.
// ----------------------------------------------------------------------------
