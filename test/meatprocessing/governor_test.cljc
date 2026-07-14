(ns meatprocessing.governor-test
  (:require [clojure.test :refer [deftest is are testing]]
            [meatprocessing.governor :as governor]
            [meatprocessing.store :as store]
            [meatprocessing.facts :as facts]))

(deftest spec-basis-violations
  (testing "No spec-basis in value -> violation"
    (let [proposal {:cites [] :value {}}
          request {:op :log-production-batch}
          violations (#'governor/spec-basis-violations request proposal)]
      (is (seq violations))
      (is (= :no-spec-basis (-> violations first :rule)))))

  (testing "Spec-basis provided -> no violation"
    (let [proposal {:cites ["FSIS-123"] :value {:jurisdiction "US"}}
          request {:op :log-production-batch}
          violations (#'governor/spec-basis-violations request proposal)]
      (is (empty? violations))))

  (testing "Only applies to real operations"
    (let [proposal {:cites [] :value {}}
          request {:op :schedule-maintenance}  ; not a real op
          violations (#'governor/spec-basis-violations request proposal)]
      (is (empty? violations)))))

(deftest batch-temp-out-of-range-violations
  (testing "Temperature within range -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:batch-temp-c 3.5
                           :product-type "fresh-beef"}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (empty? violations))))

  (testing "Temperature below range -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" {:batch-temp-c -2.0
                           :product-type "fresh-beef"}}})
          request {:op :log-production-batch :subject "batch-2"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (seq violations))
      (is (= :batch-temp-out-of-range (-> violations first :rule)))))

  (testing "Temperature above range -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-3" {:batch-temp-c 5.5
                           :product-type "fresh-poultry"}}})
          request {:op :log-production-batch :subject "batch-3"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (seq violations))
      (is (= :batch-temp-out-of-range (-> violations first :rule))))))

(deftest holding-time-exceeded-violations
  (testing "Holding time within limit -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:holding-time-hours 18 :jurisdiction "US"}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/holding-time-exceeded-violations request st)]
      (is (empty? violations))))

  (testing "Holding time exceeds limit -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" {:holding-time-hours 30 :jurisdiction "US"}}})
          request {:op :log-production-batch :subject "batch-2"}
          violations (#'governor/holding-time-exceeded-violations request st)]
      (is (seq violations))
      (is (= :holding-time-exceeded (-> violations first :rule))))))

(deftest contamination-flag-unresolved-violations
  (testing "No contamination flag -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:contamination-flag-raised? false}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/contamination-flag-unresolved-violations request st)]
      (is (empty? violations))))

  (testing "Contamination raised but resolved -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" {:contamination-flag-raised? true
                           :contamination-flag-resolved? true}}})
          request {:op :log-production-batch :subject "batch-2"}
          violations (#'governor/contamination-flag-unresolved-violations request st)]
      (is (empty? violations))))

  (testing "Contamination raised and NOT resolved -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-3" {:contamination-flag-raised? true
                           :contamination-flag-resolved? false}}})
          request {:op :log-production-batch :subject "batch-3"}
          violations (#'governor/contamination-flag-unresolved-violations request st)]
      (is (seq violations))
      (is (= :contamination-flag-unresolved (-> violations first :rule))))))

(deftest check-ok-verdict
  (testing "All checks pass -> ok? true"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:batch-temp-c 3.5
                           :product-type "fresh-beef"
                           :holding-time-hours 18
                           :jurisdiction "US"
                           :sanitation-score 85
                           :metal-detector {:passed? true :threshold-mm 2.0}
                           :contamination-flag-raised? false
                           :evidence-checklist [:batch-assay :temperature-log
                                               :holding-time-record :sanitation-log
                                               :metal-detector-pass
                                               :food-contact-surface-swab]}}})
          request {:op :log-production-batch :subject "batch-1" :stake :log-production-batch}
          proposal {:value {:jurisdiction "US"} :cites ["FSIS"] :confidence 0.85}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (true? (:ok? verdict)))
      (is (empty? (:violations verdict))))))

(deftest check-hard-violations
  (testing "Hard violations -> ok? false, hard? true"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:contamination-flag-raised? true
                           :contamination-flag-resolved? false
                           :product-type "fresh-beef"}}})
          request {:op :log-production-batch :subject "batch-1" :stake :log-production-batch}
          proposal {:value {} :cites [] :confidence 0.85}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:hard? verdict)))
      (is (seq (:violations verdict))))))

(deftest already-processed-check
  (testing "Batch already processed -> violation"
    (let [st (store/mem-store
              {:initial-batches {"batch-1" {:processed? true}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/already-processed-violations request st)]
      (is (seq violations))
      (is (= :already-processed (-> violations first :rule))))))
