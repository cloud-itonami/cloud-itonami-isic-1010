(ns meatprocessing.advisor
  "MeatProcessingAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes batch operations based on plant state and
  incoming meat lots. The advisor is SEALED into the `:advise` node of
  the operation graph; every proposal is routed through the independent
  Governor before committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (food-safety, compliance checks)
    2. Phase gate (rollout stage)
    3. Human operator (for high-stakes actions)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `refining.refiningadvisor`)."
  (:require [meatprocessing.facts :as facts]))

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with
    :op, :stake, :value, :cites, :summary, :confidence"))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op subject]} request]
      (case op
        :log-production-batch
        {:op :log-production-batch
         :stake :log-production-batch
         :value {:jurisdiction "US"
                 :batch-id subject
                 :action "Log batch into production records"}
         :cites ["FSIS-Directive-8000.200"]
         :summary "Batch intake and evidence checklist verified; ready for production logging"
         :confidence 0.85}

        :coordinate-shipment
        {:op :coordinate-shipment
         :stake :coordinate-shipment
         :value {:batch-id subject
                 :destination "Customer warehouse"
                 :transport-mode "refrigerated-truck"}
         :cites ["FSIS-Food-Safety-Modernization-Act"]
         :summary "Final product ready for shipment; cold-chain maintained throughout"
         :confidence 0.80}

        :flag-food-safety-concern
        {:op :flag-food-safety-concern
         :stake :monitoring
         :value {:batch-id subject
                 :concern-type "temperature-excursion"
                 :description "Temperature logger showed > 5C for 30 minutes during transport"
                 :recommended-action "hold-for-sensory-evaluation"}
         :cites ["FSIS-Critical-Control-Points"]
         :summary "Detected potential temperature excursion; escalating for sensory eval"
         :confidence 0.65}

        :schedule-maintenance
        {:op :schedule-maintenance
         :stake :operational
         :value {:equipment-id "MD-001"
                 :maintenance-type "metal-detector-calibration"
                 :proposed-date "2026-07-15"}
         :cites ["Equipment-Manual-MD-2024"]
         :summary "Metal detector due for scheduled calibration"
         :confidence 0.90}

        ;; fallback
        {:op op
         :stake :unknown
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
