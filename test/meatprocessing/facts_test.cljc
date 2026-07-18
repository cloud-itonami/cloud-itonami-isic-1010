(ns meatprocessing.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [meatprocessing.facts :as facts]))

(deftest jurisdiction-lookup
  (testing "Lookup valid jurisdiction"
    (let [j (facts/jurisdiction-by-id "US")]
      (is (= "US" (:id j)))
      (is (= "United States (FSIS/USDA)" (:name j)))))

  (testing "Lookup invalid jurisdiction"
    (let [j (facts/jurisdiction-by-id "XX")]
      (is (nil? j)))))

(deftest required-evidence-satisfied
  (testing "All required evidence present"
    (let [checklist [:batch-assay :temperature-log :holding-time-record
                     :sanitation-log :metal-detector-pass
                     :food-contact-surface-swab]
          satisfied (facts/required-evidence-satisfied? "US" checklist)]
      (is (true? satisfied))))

  (testing "Missing required evidence"
    (let [checklist [:batch-assay :temperature-log]
          satisfied (facts/required-evidence-satisfied? "US" checklist)]
      (is (false? satisfied))))

  (testing "Extra evidence beyond requirements"
    (let [checklist [:batch-assay :temperature-log :holding-time-record
                     :sanitation-log :metal-detector-pass
                     :food-contact-surface-swab :allergen-test]
          satisfied (facts/required-evidence-satisfied? "US" checklist)]
      (is (true? satisfied))))

  (testing "Unknown jurisdiction"
    (let [checklist [:batch-assay :temperature-log]
          satisfied (facts/required-evidence-satisfied? "XX" checklist)]
      (is (false? satisfied)))))

(deftest product-type-lookup
  (testing "Lookup valid product types"
    (are [id expected-name] (= expected-name (:name (facts/product-type-by-id id)))
      "fresh-beef" "生牛肉"
      "fresh-pork" "生豚肉"
      "fresh-poultry" "生家禽"
      "processed-sausage" "ソーセージ"))

  (testing "Lookup invalid product type"
    (let [p (facts/product-type-by-id "unknown")]
      (is (nil? p)))))

(deftest product-type-cold-chain-specs
  (testing "Fresh beef specs"
    (let [p (facts/product-type-by-id "fresh-beef")]
      (is (= -1.0 (:cold-chain-temp-min-c p)))
      (is (= 4.0 (:cold-chain-temp-max-c p)))
      (is (= 24 (:holding-time-max-hours p)))))

  (testing "Fresh poultry specs (stricter)"
    (let [p (facts/product-type-by-id "fresh-poultry")]
      (is (= -1.0 (:cold-chain-temp-min-c p)))
      (is (= 2.0 (:cold-chain-temp-max-c p)))
      (is (= 12 (:holding-time-max-hours p)))))

  (testing "Processed sausage specs (longer holding)"
    (let [p (facts/product-type-by-id "processed-sausage")]
      (is (= 2.0 (:cold-chain-temp-min-c p)))
      (is (= 5.0 (:cold-chain-temp-max-c p)))
      (is (= 48 (:holding-time-max-hours p))))))

;; ───────── Downstream Cross-Actor Handoff (optional, isic-1010 -> isic-1075) ─────────

(def ^:private well-formed-handoff
  {:handoff/id "h-1"
   :handoff/source-actor "cloud-itonami-isic-1010"
   :handoff/batch-id "batch-1"
   :handoff/product-type-id "fresh-poultry"
   :handoff/quantity-kg 500.0
   :handoff/dispatched-at-iso "2026-07-17T00:00:00Z"})

(deftest handoff-record-well-formed-test
  (testing "complete handoff passes"
    (is (true? (facts/handoff-record-well-formed? well-formed-handoff))))

  (testing "missing :handoff/quantity-kg fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/quantity-kg)))))

  (testing "non-positive quantity fails"
    (is (false? (facts/handoff-record-well-formed? (assoc well-formed-handoff :handoff/quantity-kg 0)))))

  (testing "blank batch-id fails"
    (is (false? (facts/handoff-record-well-formed? (assoc well-formed-handoff :handoff/batch-id "")))))

  (testing "nil handoff fails"
    (is (false? (facts/handoff-record-well-formed? nil)))))
