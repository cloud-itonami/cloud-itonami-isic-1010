(ns meatprocessing.store
  "Store abstraction for meat processing batches. Current implementation is
  an in-memory map; production should migrate to Datomic/kotoba-server (the
  same seam point all cloud-itonami actors use).

  A processing batch is the minimal unit of work: one delivery of meat from
  a supplier, tracked through intake, sanitation verification, processing
  operations, and shipment. Each batch has:
    - :id unique batch identifier (UUID / SKU)
    - :jurisdiction country code (US/JP/EU/etc)
    - :product-type what was delivered (fresh-beef / fresh-pork / fresh-poultry / processed-sausage)
    - :received-at timestamp when batch arrived
    - :batch-temp-c actual measured temperature at receive
    - :batch-assay true once batch source animal(s) cleared
    - :holding-time-hours cumulative time at > 5C
    - :sanitation-score 0-100, third-party audit within 7 days
    - :metal-detector-pass true if all product passed screening
    - :contamination-flag-raised? true if concern surfaced during intake/processing
    - :contamination-flag-resolved? true only if concern is verified cleared
    - :processed? true once processing operation committed
    - :shipment-finalized? true once shipment operation committed"
  (:require [clojure.set]))

;; Protocol for swappable store implementations
(defprotocol Store
  (processing-batch [store batch-id] "Retrieve a batch by ID")
  (assay-of [store batch-id] "Retrieve assay record for batch")
  (batch-already-processed? [store batch-id] "Verify batch has not been processed twice")
  (batch-shipment-finalized? [store batch-id] "Verify batch shipment not finalized twice"))

;; In-memory implementation (MemStore) for development/testing
(defrecord MemStore [batches]
  Store
  (processing-batch [_store batch-id]
    (get @batches batch-id))

  (assay-of [_store batch-id]
    (let [b (get @batches batch-id)]
      (when b
        {:batch-assay (:batch-assay b)
         :holding-time-hours (:holding-time-hours b)
         :sanitation-score (:sanitation-score b)
         :checklist (:evidence-checklist b)})))

  (batch-already-processed? [_store batch-id]
    (let [b (get @batches batch-id)]
      (true? (:processed? b))))

  (batch-shipment-finalized? [_store batch-id]
    (let [b (get @batches batch-id)]
      (true? (:shipment-finalized? b)))))

(defn mem-store
  "Create an in-memory store. `initial-batches` is an optional map of
  batch-id -> batch-record."
  [& [{:keys [initial-batches] :or {initial-batches {}}}]]
  (MemStore. (atom initial-batches)))

(defn add-batch
  "Add or update a batch in the store. Used by tests and simulation."
  [^MemStore store batch-id batch-data]
  (swap! (:batches store) assoc batch-id batch-data)
  batch-data)

(defn mark-processed
  "Mark a batch as processed (one-way flag). Used by Governor to prevent
  double-processing of the same batch."
  [^MemStore store batch-id]
  (swap! (:batches store)
         (fn [batches]
           (if (contains? batches batch-id)
             (assoc-in batches [batch-id :processed?] true)
             batches))))

(defn mark-shipment-finalized
  "Mark a batch's shipment as finalized (one-way flag). Used by Governor to
  prevent double-shipment of the same batch."
  [^MemStore store batch-id]
  (swap! (:batches store)
         (fn [batches]
           (if (contains? batches batch-id)
             (assoc-in batches [batch-id :shipment-finalized?] true)
             batches))))
