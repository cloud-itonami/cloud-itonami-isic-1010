(ns meatprocessing.phase-test
  (:require [clojure.test :refer [deftest is are testing]]
            [meatprocessing.phase :as phase]))

(deftest verdict-to-disposition
  (testing "OK verdict -> commit"
    (let [verdict {:ok? true :escalate? false :hard? false}
          disposition (phase/verdict->disposition verdict)]
      (is (= :commit disposition))))

  (testing "Escalate verdict -> escalate"
    (let [verdict {:ok? false :escalate? true :hard? false}
          disposition (phase/verdict->disposition verdict)]
      (is (= :escalate disposition))))

  (testing "Hard violations -> hold"
    (let [verdict {:ok? false :escalate? false :hard? true}
          disposition (phase/verdict->disposition verdict)]
      (is (= :hold disposition)))))

(deftest phase-0-gate
  (testing "High-stakes op in phase-0 -> hold"
    (let [request {:stake :log-production-batch}
          result (phase/gate :phase-0 request :commit)]
      (is (= :hold (:disposition result)))
      (is (= :phase-0-no-production (:reason result)))))

  (testing "Routine op in phase-0 -> allowed"
    (let [request {:stake :monitoring}
          result (phase/gate :phase-0 request :commit)]
      (is (= :commit (:disposition result)))
      (is (nil? (:reason result))))))

(deftest phase-1-gate
  (testing "High-stakes op in phase-1 -> escalate"
    (let [request {:stake :coordinate-shipment}
          result (phase/gate :phase-1 request :commit)]
      (is (= :escalate (:disposition result)))
      (is (= :phase-1-high-stakes (:reason result)))))

  (testing "Routine commit in phase-1 -> allowed"
    (let [request {:stake :monitoring}
          result (phase/gate :phase-1 request :commit)]
      (is (= :commit (:disposition result)))
      (is (nil? (:reason result)))))

  (testing "Escalate in phase-1 -> passed through"
    (let [request {:stake :monitoring}
          result (phase/gate :phase-1 request :escalate)]
      (is (= :escalate (:disposition result))))))

(deftest phase-2-gate
  (testing "Disposition passed through"
    (let [request {:stake :anything}]
      (doseq [disp [:commit :escalate :hold]]
        (let [result (phase/gate :phase-2 request disp)]
          (is (= disp (:disposition result)))
          (is (nil? (:reason result))))))))

(deftest phase-3-gate
  (testing "Disposition passed through"
    (let [request {:stake :anything}]
      (doseq [disp [:commit :escalate :hold]]
        (let [result (phase/gate :phase-3 request disp)]
          (is (= disp (:disposition result)))
          (is (nil? (:reason result))))))))

(deftest unknown-phase-gate
  (testing "Unknown phase -> conservative hold"
    (let [request {:stake :anything}
          result (phase/gate :unknown-phase request :commit)]
      (is (= :hold (:disposition result)))
      (is (= :unknown-phase (:reason result))))))
