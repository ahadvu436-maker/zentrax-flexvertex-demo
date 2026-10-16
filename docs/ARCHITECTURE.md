# Architecture

The example separates five concerns that are often collapsed in autonomous software-engineering systems:

1. **Evidence state** - local test/error evidence and the secure worktree context.
2. **Reasoning lineage** - agent runs, model version, patch proposal, and rationale.
3. **Authority state** - the exact security-policy version, individual guardrail checks, final guardrail decision, and short-lived execution authorization.
4. **Execution lineage** - git commit identity, worktree isolation, and final deployment state.
5. **Temporal replay** - a later policy version and replay decision that are appended without rewriting the historical state.

FlexVertex acts as the durable State & Provenance Substrate. The runtime components (`sentinel_core`, `cryptographic_validation`, and `compliance_guard`) are represented as contextual architecture objects; the sample does not implement their code paths.

The graph is suitable for customer-controlled or isolated environments because the meaningful decision lineage is represented locally. Building the public sample still requires the normal FlexVertex/FlexPilot installation assets unless those dependencies are pre-staged for an air-gapped deployment
