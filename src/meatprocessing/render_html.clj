(ns meatprocessing.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (`meatprocessing.operation` ->
  `meatprocessing.governor` -> `meatprocessing.phase` ->
  `meatprocessing.store`) through a multi-disposition scenario built from
  real batch shapes exercised by this repo's own tests. No langgraph
  StateGraph is present in this vertical (operation.cljc is the documented
  run-operation stub) — the harness calls `run-operation` directly, which
  is the production decision path this actor currently owns.

  No invented numbers: every table cell is read off the store or the
  audit facts that `run-operation` actually returned. Byte-identical
  across reruns against the same seed (no timestamps in page content).

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [meatprocessing.operation :as op]
            [meatprocessing.store :as store]))

;; ----------------------------- seed (from governor_test + sim) --------

(def ^:private full-us-evidence
  "US required-evidence set from meatprocessing.facts/jurisdictions."
  [:batch-assay :temperature-log :holding-time-record
   :sanitation-log :metal-detector-pass :food-contact-surface-swab])

(def ^:private clean-batch-base
  {:jurisdiction "US"
   :product-type "fresh-beef"
   :batch-temp-c 3.5
   :holding-time-hours 18
   :sanitation-score 85
   :metal-detector {:passed? true :threshold-mm 2.0}
   :contamination-flag-raised? false
   :contamination-flag-resolved? true
   :evidence-checklist full-us-evidence
   :processed? false
   :shipment-finalized? false})

(defn- seed-store
  "Seed batches that each isolate one governor path. Ids and field shapes
  match governor_test / sim — no fabricated thresholds."
  []
  (store/mem-store
   {:initial-batches
    {"batch-001"
     (assoc clean-batch-base :id "batch-001" :product-type "fresh-beef")
     ;; HARD :batch-temp-out-of-range (fresh-beef max 4.0 °C)
     "batch-hot"
     (assoc clean-batch-base :id "batch-hot" :batch-temp-c 5.5)
     ;; HARD :contamination-flag-unresolved
     "batch-contam"
     (assoc clean-batch-base
            :id "batch-contam"
            :contamination-flag-raised? true
            :contamination-flag-resolved? false)
     ;; HARD :holding-time-exceeded (US max 24 h)
     "batch-holdtime"
     (assoc clean-batch-base :id "batch-holdtime" :holding-time-hours 30)
     ;; HARD :sanitation-score-insufficient (floor 80)
     "batch-sani"
     (assoc clean-batch-base :id "batch-sani" :sanitation-score 70)}}))

(def ^:private operator
  "Phase-3 full-autonomy context: high-stakes ops still escalate via
  governor/high-stakes (log-production-batch / coordinate-shipment always
  need plant-manager sign-off). Role matches sim.cljc."
  {:actor-id "meat-processor-01"
   :role :plant-manager
   :phase :phase-3})

;; ----------------------------- harness --------------------------------

(defn- exec!
  "Run one real operation and append its audit facts to `!ledger`."
  [st !ledger request]
  (let [result (op/run-operation st request operator)]
    (swap! !ledger into (:audit result))
    result))

(defn run-demo!
  "Seed a store and drive a genuine mix of dispositions this actor reaches:

   - `:schedule-maintenance` on batch-001 → auto-commit (routine op)
   - `:flag-food-safety-concern` on batch-001 → auto-commit (monitoring)
   - `:log-production-batch` on clean batch-001 → escalate (high-stakes)
   - four DISTINCT HARD holds:
       batch-hot      → :batch-temp-out-of-range
       batch-contam   → :contamination-flag-unresolved
       batch-holdtime → :holding-time-exceeded
       batch-sani     → :sanitation-score-insufficient

  Returns `{:store st :ledger facts}` — every field `render` reads is real
  governor/phase/store output."
  []
  (let [st (seed-store)
        !ledger (atom [])]
    ;; routine op → commit
    (exec! st !ledger {:op :schedule-maintenance
                       :subject "batch-001"
                       :stake :operational})
    ;; monitoring op → commit
    (exec! st !ledger {:op :flag-food-safety-concern
                       :subject "batch-001"
                       :stake :monitoring})
    ;; clean high-stakes → always escalate for human plant-manager sign-off
    (exec! st !ledger {:op :log-production-batch
                       :subject "batch-001"
                       :stake :log-production-batch})
    ;; HARD holds (distinct rules)
    (exec! st !ledger {:op :log-production-batch
                       :subject "batch-hot"
                       :stake :log-production-batch})
    (exec! st !ledger {:op :log-production-batch
                       :subject "batch-contam"
                       :stake :log-production-batch})
    (exec! st !ledger {:op :log-production-batch
                       :subject "batch-holdtime"
                       :stake :log-production-batch})
    (exec! st !ledger {:op :log-production-batch
                       :subject "batch-sani"
                       :stake :log-production-batch})
    {:store st :ledger @!ledger}))

;; ----------------------------- rendering ------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-disposition-for
  "Last non-advisor audit fact for subject (commit / hold / approval-request)."
  [ledger subject-id]
  (->> ledger
       (remove #(= :advisor-proposal (:t %)))
       (filter #(= (:subject %) subject-id))
       last))

(defn- hold-rule [f]
  (or (some-> f :basis first)
      (some-> f :violations first :rule)
      (:phase-reason f)
      (:reason f)))

(defn- status-cell [ledger subject-id]
  (let [f (last-disposition-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (name (or (hold-rule f) :unknown))) "</span>")
      (= :approval-requested (:t f))
      (str "<span class=\"warn\">awaiting approval"
           (when-let [r (or (:reason f) (:phase-reason f))]
             (str " &middot; " (esc (name r))))
           "</span>")
      :else "<span class=\"muted\">in progress</span>")))

(defn- batch-row [st ledger batch-id]
  (let [b (store/processing-batch st batch-id)]
    (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc batch-id)
            (esc (or (:product-type b) "(missing)"))
            (esc (or (:jurisdiction b) "n/a"))
            (esc (str (or (:batch-temp-c b) "—")))
            (esc (str (or (:holding-time-hours b) "—")))
            (esc (str (or (:sanitation-score b) "—")))
            (status-cell ledger batch-id))))

(defn- ledger-row [{:keys [t op subject disposition basis violations reason phase-reason proposal-summary confidence]}]
  (let [basis-str (or (some->> basis (map #(if (keyword? %) (name %) (str %))) (str/join ", "))
                      (some->> violations first :rule name)
                      (some-> reason name)
                      (some-> phase-reason name)
                      (some-> disposition name)
                      (when proposal-summary
                        (str "conf=" confidence " · " proposal-summary))
                      "")]
    (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
            (esc (name (or t :n-a)))
            (esc (name (or op :n-a)))
            (esc (or subject ""))
            (esc basis-str))))

(def ^:private action-gate-rows
  ["        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"ok\">auto-commit when clean (routine)</span></td></tr>"
   "        <tr><td><code>:flag-food-safety-concern</code></td><td><span class=\"ok\">auto-commit when clean (monitoring; concern itself is recorded)</span></td></tr>"
   "        <tr><td><code>:log-production-batch</code></td><td><span class=\"warn\">ALWAYS human approval (high-stakes actuation) · cold-chain / holding-time / sanitation / metal-detector / contamination / evidence HARD-checked</span></td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"warn\">ALWAYS human approval (high-stakes) · already-finalized + optional handoff well-formedness HARD-checked</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a `run-demo!` result."
  [{:keys [store ledger]}]
  (let [batch-ids ["batch-001" "batch-hot" "batch-contam" "batch-holdtime" "batch-sani"]
        batch-rows (str/join "\n" (map #(batch-row store ledger %) batch-ids))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-1010 &middot; meat processing</title><style>"
     "body{font:14px/1.5 -apple-system,system-ui,sans-serif;margin:0;color:#1a1a1a;background:#f5f5f5}"
     ".bar{background:#4a1510;color:#fff;padding:1.2rem 2rem}.bar h1{margin:0;font-size:1.15rem;font-weight:600}"
     ".badge{display:inline-block;margin-top:.4rem;font-size:.75rem;opacity:.8}"
     "main{max-width:980px;margin:1.5rem auto;padding:0 1rem}"
     ".card{background:#fff;border-radius:8px;padding:1.2rem 1.4rem;margin-bottom:1.2rem;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
     ".card h2{margin-top:0;font-size:1rem}.muted{color:#777;font-size:.82rem}"
     "table{border-collapse:collapse;width:100%;font-size:.85rem}th,td{text-align:left;padding:.42rem .5rem;border-bottom:1px solid #eee}th{font-weight:600;color:#555}"
     ".ok{color:#0a7d33}.warn{color:#9a6700}.critical{color:#b41010;font-weight:600}code{background:#f0f0f0;padding:.1rem .3rem;border-radius:3px;font-size:.8rem}"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Meat processing (ISIC 1010) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · production / shipment actuation always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Processing batches</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>meatprocessing.store</code> via <code>meatprocessing.render-html</code> (<code>clojure -M:render-html</code>). Drives the real advisor → governor → phase stack. No invented usage or revenue metrics.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Product</th><th>Jurisdiction</th><th>Temp °C</th><th>Hold h</th><th>Sanitation</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     batch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Meat Processing Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Cold-chain window, holding-time, sanitation floor, metal-detector pass, contamination-flag resolution and jurisdiction evidence checklist are checked against each batch's own record; production logging and shipment always escalate for plant-manager sign-off.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every advisor proposal, hold, escalation and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        demo (run-demo!)
        html (render demo)
        out-file (java.io.File. out)]
    (.. out-file getParentFile mkdirs)
    (spit out-file html)
    (println "wrote" out "(" (count (:ledger demo)) "ledger facts )")))
