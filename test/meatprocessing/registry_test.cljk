(ns meatprocessing.registry-test
  (:require [clojure.test :refer [deftest is are testing]]
            [meatprocessing.registry :as registry]))

(deftest batch-temp-out-of-range-test
  (testing "Temperature within range"
    (is (false? (registry/batch-temp-out-of-range? 3.5 -1.0 4.0))))

  (testing "Temperature at lower bound"
    (is (false? (registry/batch-temp-out-of-range? -1.0 -1.0 4.0))))

  (testing "Temperature at upper bound"
    (is (false? (registry/batch-temp-out-of-range? 4.0 -1.0 4.0))))

  (testing "Temperature below lower bound"
    (is (true? (registry/batch-temp-out-of-range? -1.5 -1.0 4.0))))

  (testing "Temperature above upper bound"
    (is (true? (registry/batch-temp-out-of-range? 5.0 -1.0 4.0)))))

(deftest holding-time-exceeded-test
  (testing "Holding time within limit"
    (is (false? (registry/holding-time-exceeded? 18 24))))

  (testing "Holding time at limit"
    (is (false? (registry/holding-time-exceeded? 24 24))))

  (testing "Holding time exceeds limit"
    (is (true? (registry/holding-time-exceeded? 25 24)))))

(deftest sanitation-score-insufficient-test
  (testing "Score meets minimum"
    (is (false? (registry/sanitation-score-insufficient? 85 80))))

  (testing "Score at minimum"
    (is (false? (registry/sanitation-score-insufficient? 80 80))))

  (testing "Score below minimum"
    (is (true? (registry/sanitation-score-insufficient? 79 80)))))

(deftest metal-detector-pass-test
  (testing "Passed with good threshold"
    (is (true? (registry/metal-detector-pass? {:passed? true :threshold-mm 2.0}))))

  (testing "Failed screening"
    (is (false? (registry/metal-detector-pass? {:passed? false :threshold-mm 2.0}))))

  (testing "Threshold too high"
    (is (false? (registry/metal-detector-pass? {:passed? true :threshold-mm 3.0})))))

(deftest holding-time-excessive-test
  (testing "Flag hold within safe window"
    (is (false? (registry/holding-time-excessive? 1.5))))

  (testing "Flag hold at boundary"
    (is (false? (registry/holding-time-excessive? 2.0))))

  (testing "Flag hold exceeds safe window"
    (is (true? (registry/holding-time-excessive? 2.5)))))
