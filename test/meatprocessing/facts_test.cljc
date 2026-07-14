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
