# Autonomous Code Change Decision Lineage

This compact FlexVertex project demonstrates how autonomous software-engineering actions can be reconstructed from the exact evidence, model, policy, guardrail, authorization, git, and deployment state that governed them.

The scenario was proposed for a ZentraX AI secure-runtime architecture in which autonomous agents modify code and create git commits inside an isolated worktree. The central question is:

> **Can every autonomous action be traced from observed evidence through model reasoning and security authority to the exact commit and deployment state that resulted?**

At the historical boundary, a synthetic security regression detects that an exception-path audit call in `sentinel_core` could emit an unredacted email field before the normal redaction helper runs. A chain of autonomous agents observes the failure, diagnoses it, proposes a minimal patch, evaluates that patch against the policy in force, commits it inside an isolated worktree, and deploys it to the local secure runtime.

A later policy revision adds a human-approval requirement for changes touching PII-processing code. Replaying the same patch under the later policy produces a different disposition without rewriting the historical decision.

All source paths, errors, agents, model versions, policies, hashes, commits, and deployment records in this repository are synthetic.

## What this example demonstrates

- multi-agent execution lineage across observation, diagnosis, patching, guardrails, commit, and deployment;
- exact model-version and secure-worktree context for reasoning runs;
- first-class patch rationale grounded in local error evidence;
- security guardrails as inspectable checks rather than a single opaque pass/fail flag;
- the governing policy version and execution authorization that applied at t0;
- immutable git-commit and deployment-state lineage;
- document and embedded-passage provenance;
- later policy evolution without ex-post-facto rewriting of the historical action; and
- a customer-controlled graph suitable for isolated or sovereign runtime architectures.

The project models decision/provenance state around a ZentraX-style secure runtime; it does not implement or benchmark ZentraX `sentinel_core`, cryptographic validation, or `compliance_guard` components.

## Repository layout

```text
.
├── README.md
├── build.gradle
├── gradle.properties
├── FlexVertex-Example-Code-License.pdf
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DATA-MODEL.md
│   ├── EXECUTIVE-OVERVIEW.md
│   ├── SCENARIO.md
│   └── diagrams/
├── scenario/
│   ├── autonomous-code-change-decision-lineage.yaml
│   ├── objects/
│   ├── relationships/
│   ├── generated/
│   │   ├── documents/
│   │   └── voyages/
│   └── icons/
├── scripts/
│   ├── build.sh
│   └── run-scenario.sh
└── src/
    └── factory/java/
        └── FlexVertexYamlEnterpriseFactory.java
```

## Prerequisites

The canonical walkthrough uses Linux.

Before starting, install:

- Docker Engine with Docker Compose V2
- Git

You do **not** need to install Java or Gradle separately. FlexPilot supplies the Java and Gradle environment used by this example.

## Install FlexPilot and FlexVertex Iron

Create a working layout:

```bash
mkdir -p flexvertex/flexpilot flexvertex/iron flexvertex/workspace
cd flexvertex/flexpilot
```

Download FlexPilot:

```bash
curl -LO https://assets.flexvertex.com/installers/pilot/linux/amd64/2.1.x/flexpilot
chmod +x flexpilot
./flexpilot version
```

Optionally add FlexPilot to the current shell's `PATH`:

```bash
export PATH="$PWD:$PATH"
flexpilot version
```

Choose an administrator password and keep it in the shell:

```bash
export FV_ADMIN_PASSWORD='choose-an-admin-password'
```

Install FlexVertex Iron with the embedding profile. This project includes evidence PDFs that are embedded into the graph, so the `emb` profile is required.

```bash
flexpilot install \
  --dir ../iron \
  --edition Iron \
  --adminPasswd "$FV_ADMIN_PASSWORD" \
  --profile emb
```

Start the services:

```bash
cd ../iron
docker compose up -d
docker compose ps
```

The FlexVertex service should report `healthy`, and FlexVertex Commander should be running. A one-time asset-loader container may complete and exit normally after installation.

Commander is exposed on port `8080`; the FlexVertex service uses ports `8000` and `10000` in the standard Iron configuration used by this walkthrough.

Move to the workspace where you will place or clone this repository:

```bash
cd ../workspace
```

After cloning or extracting the project, change into its repository root before running the remaining commands.

If `flexpilot` is not on your `PATH`, set `FLEXPILOT` to its path, for example:

```bash
export FLEXPILOT=../flexpilot/flexpilot
```

The scripts honor that variable.

A local `.env` file is not required for the normal public build. The build defaults to the anonymous public FlexVertex Maven repositories.

## Build

From the repository root:

```bash
./scripts/build.sh
```

This creates the runnable Factory JAR under:

```text
build/libs/FlexVertexYamlEnterpriseFactory-1.0.0.jar
```

## Materialize the scenario

If this is a new shell, set the administrator password used when Iron was installed:

```bash
export FV_ADMIN_PASSWORD='your-Iron-admin-password'
```

Then run:

```bash
./scripts/run-scenario.sh
```

The scenario is created under:

```text
Domain: FlexVertexZentraXDemo
Nexus:  AutonomousSoftwareEngineering
Schema: SecureCodeChangeLineage
```

Full D/N/S:

```text
/FlexVertexZentraXDemo/AutonomousSoftwareEngineering/SecureCodeChangeLineage
```

## Explore the graph in FlexVertex Commander

Open:

```text
http://localhost:8080
```

Log in with the synthetic demonstration account:

```text
Username: /FlexVertexZentraXDemo/AutonomousSoftwareEngineering/SecureCodeChangeLineage/DecisionLineageExplorer
Password: abc123
```

After materialization, open **Assets -> Home**. The generated assets include embedded evidence documents, Voyager traversals, and semantic class icons.

Start with:

```text
Voyager/01-full-t0-decision-lineage.voyage
Voyager/02-error-to-patch-rationale.voyage
Voyager/03-pre-execution-guardrail-checks.voyage
Voyager/04-commit-and-deployment-lineage.voyage
Voyager/05-multi-agent-run-chain.voyage
Voyager/07-current-policy-replay.voyage
Voyager/08-t0-versus-current-policy.voyage
```

## What to look for

At t0, trace `ERR-PII-482` into `RAT-482` and `PATCH-482`, then inspect `GD-482-T0`. The guardrail decision should be governed by policy v3.6 and connected to six required checks, all of which pass.

Follow the decision to `AUTH-CODE-482`, then into the Commit Agent and Deployment Agent runs. The final execution lineage should terminate at commit `CMT-482` and deployment `DEP-482` with state `DEPLOYED_HEALTHY`.

Then inspect the later/current path. Policy v3.7 adds an explicit human security approval requirement for PII-processing code. The same patch therefore receives `HOLD_FOR_HUMAN_SECURITY_APPROVAL` in `GD-482-REPLAY`. That replay is not an executed deployment and must not be projected backward into the historical decision.

## Evidence and provenance

Seven generated evidence PDFs and their embedded chunks connect the local regression, patch rationale, governing policy, pre-execution decision, git commit, deployment receipt, and later policy revision back into the graph.

`chunkBelongsToDocument` records structural document provenance. `chunkSupportsEntity` records passage/entity associations with evidence role, confidence, match method, and authority metadata.

## Documentation

- [`docs/SCENARIO.md`](docs/SCENARIO.md) - narrative, timing, assumptions, and reconstruction questions
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - separation of evidence, reasoning, authority, execution, and temporal replay
- [`docs/DATA-MODEL.md`](docs/DATA-MODEL.md) - classes and relationship semantics
- [`docs/EXECUTIVE-OVERVIEW.md`](docs/EXECUTIVE-OVERVIEW.md) - concise non-code overview
- [`docs/diagrams/MODEL-GUIDE.md`](docs/diagrams/MODEL-GUIDE.md) - graph-reading guide and Voyager starting points
- [`docs/diagrams/`](docs/diagrams/) - Mermaid model and workflow diagrams

## Stop and restart FlexVertex

You do not need to reinstall FlexVertex between runs.

From the sample-project repository root, return to the Iron installation directory:

```bash
cd ../../iron
```

To stop the running FlexVertex containers without removing them:

```bash
docker compose stop
```

To start FlexVertex again later:

```bash
docker compose up -d
docker compose ps
```

Wait for `flexvertexservice` to report `healthy` before rerunning the example or opening FlexVertex Commander.

The previously materialized scenario remains available across normal stop/start cycles. Rerun `./scripts/run-scenario.sh` only when you intentionally want to recreate the demonstration graph.

## Synthetic-data notice

This repository is an architecture and data-model demonstration, not a source-code security product and not an implementation of ZentraX runtime controls.

All source paths, errors, users, agents, model versions, policies, hashes, commits, deployments, documents, and timestamps are synthetic demonstration data.
