# Data Model

The graph is organized around a small number of first-class concepts:

- `AutonomousAgent` and `AgentRun` identify who/what performed each stage of the autonomous workflow.
- `AgentModelVersion` records the exact local reasoning model used by the reasoning agents.
- `SecureWorktree` preserves repository, branch, base-commit, isolation, and egress state.
- `ErrorEvent`, `PatchCandidate`, and `PatchRationale` preserve the evidence-to-change chain.
- `SecurityPolicyVersion`, `GuardrailCheck`, `GuardrailDecision`, and `ExecutionAuthorization` preserve the policy/authority chain.
- `GitCommit` and `DeploymentState` preserve execution outcomes.
- `DecisionStateSnapshot` provides explicit historical and replay boundaries.
- `DocumentEvidence` and factory-created `EmbeddingChunk` objects provide document/passage provenance.

A later `PolicyRevisionEvent` introduces policy v3.7, which supersedes v3.6. The current replay decision uses v3.7 but remains distinct from the executed t0 decision
