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

;; ───────── Verified primary-source citations (2026-07-25) ─────────

(deftest every-jurisdiction-is-cited
  (testing "all three jurisdictions carry a legal-basis and a fetchable provenance URL"
    (doseq [id (keys facts/jurisdictions)]
      (is (true? (facts/cited? id))
          (str id " must rest on a verified primary source"))))

  (testing "unknown jurisdiction is neither cited nor invented"
    (is (nil? (facts/spec-basis "XX")))
    (is (false? (facts/cited? "XX")))))

(deftest citation-coverage-is-honest
  (testing "coverage counts cited jurisdictions, and reports unknowns as missing"
    (let [c (facts/citation-coverage)]
      (is (= 3 (:known c)))
      (is (= 3 (:cited c)))
      (is (= ["EU" "JP" "US"] (:cited-jurisdictions c)))
      (is (= [] (:uncited-jurisdictions c)))))

  (testing "an unknown jurisdiction is surfaced, not silently dropped"
    (let [c (facts/citation-coverage ["US" "XX"])]
      (is (= 2 (:requested c)))
      (is (= 1 (:known c)))
      (is (= ["XX"] (:unknown-jurisdictions c))))))

(deftest jp-storage-limit-matches-the-kokuji
  (testing "保存基準 3(1) is 4゜以下 -- the earlier unsourced 5.0 was looser than the 告示"
    (let [j (facts/jurisdiction-by-id "JP")]
      (is (= 4.0 (:cold-chain-max-temp-c j)))
      (is (= 4.0 (-> j :statutory-limits :storage-max-temp-c)))
      (is (= -15.0 (-> j :statutory-limits :frozen-storage-max-temp-c)))
      (is (= 10.0 (-> j :statutory-limits :processing-surface-max-temp-c))))))

(deftest eu-operative-limit-is-not-loosened-to-the-carcase-ceiling
  (testing "Annex III allows 7.0 degC for carcases, but the operative gate stays at the 3.0 offal ceiling"
    (let [j (facts/jurisdiction-by-id "EU")
          limits (:statutory-limits j)]
      (is (= 7.0 (:carcase-max-temp-c limits)))
      (is (= 3.0 (:offal-max-temp-c limits)))
      (is (= 4.0 (:poultry-max-temp-c limits)))
      (is (= 3.0 (:cold-chain-max-temp-c j))
          "research must never relax a food-safety gate")
      (is (<= (:cold-chain-max-temp-c j) (:carcase-max-temp-c limits))))))

(deftest us-chilling-rule-is-performance-based
  (testing "9 CFR 381.66(b)(1)(i) sets no flat numeric carcass ceiling"
    (let [limits (:statutory-limits (facts/jurisdiction-by-id "US"))]
      (is (= :performance-based-no-pathogen-outgrowth (:chilling-rule limits)))
      (is (= 36.0 (:fresh-frozen-holding-max-temp-f limits)))
      (is (= 0.0 (:frozen-core-target-temp-f limits)))
      (is (= -10.0 (:warm-packaged-plate-freezer-max-temp-f limits))))))

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
