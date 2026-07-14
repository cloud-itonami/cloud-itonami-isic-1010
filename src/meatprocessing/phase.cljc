(ns meatprocessing.phase
  "Phase gate for the meat-processing actor rollout (0->3 maturity).
  Phase 0: simulation/test only (no real batch processing)
  Phase 1: supervised operation (high-stakes ops require pre-approval)
  Phase 2: reduced supervision (escalate only on conflict/low confidence)
  Phase 3: full autonomy (LLM confidence sufficient for routine ops)

  See `operation.cljc` for how the phase gate is invoked. Real production
  batches (`:log-production-batch` / `:coordinate-shipment`) ALWAYS require
  human sign-off regardless of phase (that's a separate `high-stakes?` gate)."
  (:require [meatprocessing.governor :as governor]))

(def default-phase :phase-0)

(defn verdict->disposition
  "Translate Governor verdict to initial disposition before phase gate.
  Governor's `:ok?` -> `:commit` (no issues)
  Governor's `:escalate?` -> `:escalate` (human review needed)
  Any `:hard?` violations -> `:hold` (already rejected by Governor)"
  [{:keys [ok? escalate? hard?]}]
  (cond
    hard? :hold
    escalate? :escalate
    :else :commit))

(defn gate
  "Phase gate: given the Governor's verdict and current phase, return
  {:disposition :commit|:hold|:escalate :reason nil|keyword}

  Phase 0 (Simulation):
    - All real-world operations (`:log-production-batch`, `:coordinate-shipment`)
      escalate to hold (no production operation in test phase)
    - Other ops (monitoring, flagging) allowed if Governor approves

  Phase 1 (Supervised):
    - High-stakes ops escalate even if clean
    - Other ops allowed if Governor approves

  Phase 2 (Reduced Supervision):
    - Escalate only on Governor violation or low confidence
    - Routine batch ops allowed if Governor approves

  Phase 3 (Full Autonomy):
    - Commit if Governor approves, escalate only on Guard violation"
  [phase request disposition]
  (case phase
    :phase-0
    (if (contains? governor/high-stakes (:stake request))
      {:disposition :hold :reason :phase-0-no-production}
      {:disposition disposition :reason nil})

    :phase-1
    (if (contains? governor/high-stakes (:stake request))
      {:disposition :escalate :reason :phase-1-high-stakes}
      {:disposition disposition :reason nil})

    :phase-2
    {:disposition disposition :reason nil}

    :phase-3
    {:disposition disposition :reason nil}

    ;; default: unknown phase -> conservative hold
    {:disposition :hold :reason :unknown-phase}))
