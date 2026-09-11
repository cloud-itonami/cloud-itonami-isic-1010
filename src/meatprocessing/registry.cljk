(ns meatprocessing.registry
  "Pure validation functions for meat processing parameters. These are called
  by the Governor to independently verify physical/operational constraints --
  the LLM advisor's confidence is NOT sufficient to override these checks."
  (:require [meatprocessing.facts :as facts]))

(defn batch-temp-out-of-range?
  "Independently verify that the batch's actual temperature stays inside
  the declared cold-chain window [min,max]. Both bounds are inclusive.
  The aerospace two-sided-tolerance discipline: both lower (thermal abuse)
  and upper (condensation/mold risk) bounds are HARD limits."
  [actual-temp-c min-temp-c max-temp-c]
  (or (< actual-temp-c min-temp-c)
      (> actual-temp-c max-temp-c)))

(defn holding-time-exceeded?
  "Independently verify that the batch's actual time at ambient temperature
  does not exceed the jurisdiction's maximum holding-time. Time zero is when
  the batch first reaches the processing plant; time is recorded continuously
  via temperature logger (every 15 minutes by default)."
  [actual-hours-held max-hours-allowed]
  (> actual-hours-held max-hours-allowed))

(defn sanitation-score-insufficient?
  "Independently verify that the plant's pre-processing sanitation score
  meets the minimum required by jurisdiction. Score is 0-100, assessed by
  a third-party auditor against FSIS/EFSA/MHLW sanitation standards within
  7 days prior to batch processing."
  [actual-score min-score-required]
  (< actual-score min-score-required))

(defn metal-detector-pass?
  "Metal detector screening is a two-stage check:
  1. All product must pass the threshold (no ferrous/non-ferrous > 2.4mm)
  2. Sensitivity setting must be documented
  Returns true only if both are satisfied."
  [{:keys [passed? threshold-mm]}]
  (and (true? passed?) (<= threshold-mm 2.4)))

(defn holding-time-excessive?
  "For a batch with contamination concern raised, verify that the batch has
  not sat idle beyond a safe holding window after contamination was flagged.
  If a concern is raised and more than 2 hours pass without being resolved,
  the batch cannot be processed (risk of pathogen multiplication)."
  [hours-since-flagged]
  (> hours-since-flagged 2.0))
