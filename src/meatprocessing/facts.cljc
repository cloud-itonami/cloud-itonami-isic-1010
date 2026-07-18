(ns meatprocessing.facts
  "Reference facts for meat processing: jurisdiction requirements for batch
  processing, cold-chain integrity, holding-time compliance, and food-safety
  evidence. This namespace contains pure lookup functions for regulatory
  compliance checks -- the Governor calls these to validate proposals against
  jurisdiction requirements."
  (:require [clojure.string :as str]))

(def jurisdictions
  "Meat processing jurisdictions and their required documentation/evidence
  checklist requirements."
  {"US"
   {:id "US"
    :name "United States (FSIS/USDA)"
    :cold-chain-max-temp-c 4.0
    :holding-time-max-hours 24
    :required-evidence
    [:batch-assay           ;; source animal health/age/provenance
     :temperature-log       ;; cold-chain temperature records
     :holding-time-record   ;; time from receive to processing start
     :sanitation-log        ;; pre-processing plant sanitation
     :metal-detector-pass   ;; foreign material screening
     :food-contact-surface-swab]} ;; hygiene verification

   "JP"
   {:id "JP"
    :name "日本 (MHLW/厚生労働省)"
    :cold-chain-max-temp-c 5.0
    :holding-time-max-hours 24
    :required-evidence
    [:batch-assay
     :temperature-log
     :holding-time-record
     :sanitation-log
     :metal-detector-pass
     :food-contact-surface-swab]}

   "EU"
   {:id "EU"
    :name "European Union (EFSA)"
    :cold-chain-max-temp-c 3.0
    :holding-time-max-hours 24
    :required-evidence
    [:batch-assay
     :temperature-log
     :holding-time-record
     :sanitation-log
     :metal-detector-pass
     :food-contact-surface-swab
     :allergen-test]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that all required-evidence items are present in the batch's
  checklist. Returns true only if every item in the jurisdiction's
  required-evidence list is present in the batch's checklist."
  [jurisdiction-id checklist]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (if-not j
      false
      (let [required (set (:required-evidence j))
            present (set checklist)]
        (clojure.set/subset? required present)))))

(def product-types
  "Valid meat product categories and their required processing parameters."
  {"fresh-beef"
   {:id "fresh-beef"
    :name "生牛肉"
    :cold-chain-temp-min-c -1.0
    :cold-chain-temp-max-c 4.0
    :holding-time-max-hours 24}

   "fresh-pork"
   {:id "fresh-pork"
    :name "生豚肉"
    :cold-chain-temp-min-c -1.0
    :cold-chain-temp-max-c 4.0
    :holding-time-max-hours 24}

   "fresh-poultry"
   {:id "fresh-poultry"
    :name "生家禽"
    :cold-chain-temp-min-c -1.0
    :cold-chain-temp-max-c 2.0
    :holding-time-max-hours 12}

   "processed-sausage"
   {:id "processed-sausage"
    :name "ソーセージ"
    :cold-chain-temp-min-c 2.0
    :cold-chain-temp-max-c 5.0
    :holding-time-max-hours 48}})

(defn product-type-by-id [id]
  (get product-types id))

;; ─────────── Downstream Cross-Actor Handoff (optional, isic-1010 -> isic-1075) ───────────
;;
;; `:coordinate-shipment` proposals MAY OPTIONALLY carry a `:handoff`
;; record under the proposal's `:value` when this actor dispatches a
;; finished batch of processed poultry/beef to a downstream cook-chill/
;; cook-freeze prepared-meal manufacturer (e.g. cloud-itonami-isic-1075).
;; Reuses the SAME `:handoff/*` wire shape isic-1075 itself already uses
;; for its own downstream isic-1075<->jsic-4721 handoff -- see
;; superproject ADR-2800000800. Unlike that mandatory pairing, a
;; `:handoff` here is OPTIONAL, not required: this actor's shipment
;; proposals worked before this field existed and keep working
;; unchanged with no `:handoff` attached at all.
;;
;;   {:handoff/id "..."
;;    :handoff/source-actor "cloud-itonami-isic-1010"
;;    :handoff/batch-id "..."
;;    :handoff/product-type-id "fresh-poultry"
;;    :handoff/quantity-kg 500.0
;;    :handoff/dispatched-at-iso "..."
;;    :handoff/cold-chain-temp-min-c -1.0    ; OPTIONAL, pass-through only
;;    :handoff/cold-chain-temp-max-c 4.0}    ; OPTIONAL, pass-through only

(defn handoff-record-well-formed?
  "Positive-sense convenience predicate: does `handoff` carry every
  REQUIRED `:handoff/*` field (id/source-actor/batch-id/product-type-id/
  quantity-kg/dispatched-at-iso) with a plausible value (quantity-kg a
  positive number, the string fields non-blank)? Never validates the
  OPTIONAL cold-chain/unspsc/gtin fields."
  [handoff]
  (boolean
   (and (map? handoff)
        (seq (:handoff/id handoff))
        (seq (:handoff/source-actor handoff))
        (seq (:handoff/batch-id handoff))
        (some? (:handoff/product-type-id handoff))
        (number? (:handoff/quantity-kg handoff))
        (pos? (:handoff/quantity-kg handoff))
        (seq (:handoff/dispatched-at-iso handoff)))))
