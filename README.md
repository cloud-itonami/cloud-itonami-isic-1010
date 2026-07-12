# cloud-itonami-isic-1010

Open Business Blueprint for **ISIC 1010**: processing and preserving
of meat — the representative *food-manufacturing* (食) vertical of the
衣食住 scaffold batch (ADR-2607122200).

**Maturity: `:blueprint`** — this repository publishes the business
blueprint only. There is **no actor implementation yet**, and none is
claimed. ISIC division 10-12 (food) sits in **rollout Wave 3
(production/robotics)** of the reverse-toposort plan (ADR-2607121000):
implementation is gated on the robotics premise (ADR-2607011000) — a
real robot fleet plus an independent governor with an accident-free
audit ledger. Publishing the blueprint now is deliberate ammunition
loading for when that gate opens (ADR-2607122100 Track A).

## What the implemented actor will be

**MeatOps-LLM ⊣ Meat Processing Governor** — the fleet-standard
pattern: the advisor LLM drafts intake, HACCP/inspection scheduling,
lot traceability and recall assessments; the independent
`:meat-processing-governor` (a keyword unique fleet-wide) gates every
action; physical-domain work (cutting, packing, cold-chain handling)
is executed by robots under `kotoba-lang/robotics` safety classes,
never dispatched directly by the LLM. Food-safety-critical actions
require human sign-off.

Operating states: `intake → design → produce → inspect → package → audit`.

## Why open

AGPL-3.0-or-later, forkable by any qualified operator, so local food
processors never surrender production and traceability data to a
closed SaaS. Part of the [cloud-itonami](https://itonami.cloud) open
business fleet.
