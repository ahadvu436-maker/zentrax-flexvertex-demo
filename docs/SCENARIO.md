# Scenario: Autonomous Code Change Decision Lineage

## Purpose

This synthetic scenario models an autonomous multi-agent software-engineering episode inside a secure, isolated git worktree. It is designed to answer a practical audit question: **can every system action be traced back to the evidence, rationale, security checks, governing policy, execution authority, commit, and deployment state that actually applied when the action occurred?**

The scenario is inspired by the ZentraX AI secure-runtime architecture supplied with the request. The FlexVertex project does not implement ZentraX controls; it models the decision and provenance state around a representative autonomous code-change workflow.

## Historical episode

At 2026-09-12T18:14:22.100Z, a local security regression detects a synthetic exception path in `sentinel_core` that could log an unredacted email field before redaction. Network egress is disabled and no real PII is retained.

Six autonomous agents participate in the repair episode: observation, diagnosis, patch generation, security guard, commit, and deployment. The proposed patch is narrow: route the exception-path audit metadata through the existing redaction helper and add a regression test.

At the historical decision boundary, Secure Autonomous Code Change Policy v3.6 is governing. The Security Guard Agent records six required checks, all of which pass, and issues a short-lived authorization to commit and deploy the exact patch inside the isolated environment. The resulting commit and deployment are recorded as first-class objects.

## Later policy state

Policy v3.7 becomes effective on 2026-09-15. It adds an explicit human-security-approval requirement for code changes touching PII-processing paths. A later replay of the same patch therefore returns `HOLD_FOR_HUMAN_SECURITY_APPROVAL`.

That later result is intentionally a simulation. It does not rewrite the historical v3.6 decision or imply that the earlier execution used future policy.

## Questions this example is designed to answer

1. Which error evidence triggered the repair episode?
2. Which autonomous agent runs participated, and which model version did the reasoning agents use?
3. What patch was proposed and what rationale was recorded?
4. Which security policy governed the pre-execution check at t0?
5. Which deterministic guardrail checks passed before commit/deployment authority was issued?
6. Which authorization enabled the git commit and deployment?
7. Which exact commit reached the isolated runtime?
8. What was the final deployment state?
9. Which documents and embedded passages support the graph assertions?
10. Why does the same patch receive a different disposition under current policy?
11. Can the original execution be reconstructed without importing the later human-approval rule backward into t0?

## Synthetic-data boundary

All repository names, source paths, hashes, agent/model identifiers, errors, policies, commits, and deployment events are synthetic. No real ZentraX source code, credentials, user data, or infrastructure state is included.
