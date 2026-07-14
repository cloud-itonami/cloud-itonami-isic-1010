(ns meatprocessing.operation
  "OperationActor -- one meat-processing operation = one supervised actor run,
  expressed as a langgraph-clj StateGraph. The advisor (MeatProcessingAdvisor)
  is sealed into a single node (:advise); its proposal is ALWAYS routed
  through the Meat Processing Governor (:govern) and the rollout phase gate
  (:decide) before anything commits to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (MemStore today; Datomic/kotoba-server is the next seam)
    - the Advisor  (mock | real LLM)
    - the Phase    (0->3 rollout)

  One graph run = one meat-processing operation (intake -> advise -> govern ->
  decide -> commit | hold | approval). No unbounded inner loop -- each
  operation is auditable and checkpointed. A batch's life is advanced by
  MANY operations (flag-concern / log-batch / shipment), each its own
  independent run.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor and hands the
  decision to a human operator (plant manager / food-safety officer). The
  approver resumes with `{:approval {:status :approved}}` (or :rejected).
  `:log-production-batch`/`:coordinate-shipment` ALWAYS reach this node
  when the Governor is clean -- see `meatprocessing.phase`.

  NOTE: langgraph integration is deferred. This stub version defines the
  high-level flow; production build requires langgraph-clj."
  (:require [meatprocessing.advisor :as advisor]
            [meatprocessing.governor :as governor]
            [meatprocessing.phase :as phase]
            [meatprocessing.store :as store]))

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:subject request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn run-operation
  "Run one meat-processing operation through the advisor -> governor -> phase
  gate -> decision flow. Returns a map with :disposition, :audit, :record.

  This is the core synchronous flow that will be embedded in the langgraph
  StateGraph in production. For testing/development, can be called directly."
  [store request context & [{:keys [advisor]
                             :or {advisor (advisor/mock-advisor)}}]]
  (let [;; Step 1: Advisor proposes
        proposal (advisor/-advise advisor store request)
        advisor-trace (advisor/trace request proposal)

        ;; Step 2: Governor censors
        verdict (governor/check request context proposal store)

        ;; Step 3: Phase gate applies rollout constraints
        base-disposition (phase/verdict->disposition verdict)
        ph (:phase context phase/default-phase)
        {:keys [disposition reason]} (phase/gate ph request base-disposition)

        ;; Step 4: Assemble result
        disposition-fact (case disposition
                           :hold (cond-> (governor/hold-fact request context verdict)
                                   reason (assoc :phase-reason reason :phase ph))
                           :escalate {:t :approval-requested
                                      :op (:op request) :subject (:subject request)
                                      :reason (or reason
                                                  (cond (:high-stakes? verdict) :actuation
                                                        :else :low-confidence))}
                           :commit (commit-fact request context proposal))
        record (when (= :commit disposition)
                 (commit-record request context proposal))]
    {:disposition disposition
     :audit [advisor-trace disposition-fact]
     :record record
     :verdict verdict}))

(defn build
  "Stub for building a langgraph-clj StateGraph. Production implementation
  requires langgraph. This version provides the flow logic via run-operation
  for testing. opts:
    :advisor -- a `meatprocessing.advisor/Advisor` (default: mock-advisor)"
  [store & [opts]]
  ;; Return a function that mimics the graph interface
  (fn invoke-operation [request context]
    (run-operation store request context opts)))
