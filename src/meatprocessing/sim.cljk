(ns meatprocessing.sim
  "Simple simulation/demo runner for the MeatProcessingActor. Used to validate
  that the actor graph compiles and basic proposal flow works."
  (:require [meatprocessing.operation :as operation]
            [meatprocessing.store :as store]
            [meatprocessing.phase :as phase]))

(defn demo
  "Run a simple demo scenario: create a batch, propose logging it, and check
  the verdict flow."
  []
  (let [;; Create store with a test batch
        st (store/mem-store
            {:initial-batches
             {"batch-001"
              {:id "batch-001"
               :jurisdiction "US"
               :product-type "fresh-beef"
               :batch-temp-c 3.5
               :holding-time-hours 18
               :sanitation-score 85
               :metal-detector {:passed? true :threshold-mm 2.0}
               :contamination-flag-raised? false
               :contamination-flag-resolved? true
               :evidence-checklist [:batch-assay :temperature-log :holding-time-record
                                    :sanitation-log :metal-detector-pass
                                    :food-contact-surface-swab]
               :processed? false}}})

        ;; Build actor
        actor (operation/build st)

        ;; Create a request to log the batch
        request {:op :log-production-batch
                 :subject "batch-001"
                 :stake :log-production-batch}

        ;; Context with phase 0 (simulation)
        context {:actor-id "meat-processor-01"
                 :role :plant-manager
                 :phase :phase-0}]

    (println "=== Meat Processing Actor Demo ===")
    (println "Demo batch: batch-001")
    (println "Request: log-production-batch")
    (println "Phase: phase-0 (simulation)")
    (println "Expected: escalate (phase-0 blocks production ops)")
    (println)))

(comment
  ;; In a real REPL:
  (demo)
)
