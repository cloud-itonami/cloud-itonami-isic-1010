(ns meatprocessing.store-test
  (:require [clojure.test :refer [deftest is are testing]]
            [meatprocessing.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial batches"
    (let [batches {"batch-1" {:id "batch-1" :product-type "fresh-beef"}}
          st (store/mem-store {:initial-batches batches})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest processing-batch-retrieval
  (testing "Retrieve existing batch"
    (let [batch-data {:id "batch-1" :product-type "fresh-beef"}
          st (store/mem-store {:initial-batches {"batch-1" batch-data}})
          retrieved (store/processing-batch st "batch-1")]
      (is (= batch-data retrieved))))

  (testing "Retrieve non-existent batch"
    (let [st (store/mem-store)
          retrieved (store/processing-batch st "no-such-batch")]
      (is (nil? retrieved)))))

(deftest add-batch
  (testing "Add new batch to store"
    (let [st (store/mem-store)
          batch-data {:id "batch-1" :product-type "fresh-pork"}
          result (store/add-batch st "batch-1" batch-data)]
      (is (= batch-data result))
      (is (= batch-data (store/processing-batch st "batch-1")))))

  (testing "Update existing batch"
    (let [st (store/mem-store {:initial-batches {"batch-1" {:id "batch-1"}}})
          updated-data {:id "batch-1" :product-type "fresh-beef"}
          result (store/add-batch st "batch-1" updated-data)]
      (is (= updated-data result))
      (is (= updated-data (store/processing-batch st "batch-1"))))))

(deftest assay-of-retrieval
  (testing "Retrieve assay for existing batch"
    (let [batch-data {:batch-assay true :holding-time-hours 18
                      :sanitation-score 85
                      :evidence-checklist [:batch-assay :temperature-log]}
          st (store/mem-store {:initial-batches {"batch-1" batch-data}})
          assay (store/assay-of st "batch-1")]
      (is (= true (:batch-assay assay)))
      (is (= 18 (:holding-time-hours assay)))
      (is (= 85 (:sanitation-score assay)))
      (is (= [:batch-assay :temperature-log] (:checklist assay)))))

  (testing "Retrieve assay for non-existent batch"
    (let [st (store/mem-store)
          assay (store/assay-of st "no-such-batch")]
      (is (nil? assay)))))

(deftest batch-processed-tracking
  (testing "New batch not processed"
    (let [st (store/mem-store {:initial-batches {"batch-1" {}}})
          already-processed (store/batch-already-processed? st "batch-1")]
      (is (false? already-processed))))

  (testing "Mark batch as processed"
    (let [st (store/mem-store {:initial-batches {"batch-1" {}}})
          _ (store/mark-processed st "batch-1")
          already-processed (store/batch-already-processed? st "batch-1")]
      (is (true? already-processed))))

  (testing "Non-existent batch returns false"
    (let [st (store/mem-store)
          already-processed (store/batch-already-processed? st "no-batch")]
      (is (false? already-processed)))))

(deftest batch-shipment-finalized-tracking
  (testing "New batch shipment not finalized"
    (let [st (store/mem-store {:initial-batches {"batch-1" {}}})
          finalized (store/batch-shipment-finalized? st "batch-1")]
      (is (false? finalized))))

  (testing "Mark batch shipment as finalized"
    (let [st (store/mem-store {:initial-batches {"batch-1" {}}})
          _ (store/mark-shipment-finalized st "batch-1")
          finalized (store/batch-shipment-finalized? st "batch-1")]
      (is (true? finalized))))

  (testing "Non-existent batch returns false"
    (let [st (store/mem-store)
          finalized (store/batch-shipment-finalized? st "no-batch")]
      (is (false? finalized)))))
