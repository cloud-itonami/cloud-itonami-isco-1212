(ns hr-management.actor
  "HRManagementActor — the ISCO-08 1212 independent HR-management actor
  as a `langgraph.graph/state-graph` (ADR-2607011000 / CLAUDE.md Actors
  section). One graph run = one HR operation request (intake -> advise
  -> govern -> decide -> commit/hold, with a human-approval interrupt
  for escalated proposals). No infinite internal loop; checkpointed per
  superstep so an interrupted run can resume after human sign-off.
  Modeled on cloud-itonami-isco-1213's policyplan.actor /
  cloud-itonami-isco-4311's bookkeeping.actor, with a rollout-phase gate
  (`hr-management.phase`) spliced into :decide the way
  cloud-itonami-isco-1321's manufacturing-floor.operation splices in
  `manufacturing-floor.phase`.

  ```text
  :intake -> :advise -> :govern -> :decide -+-> :commit            (:disposition :commit)
                                             +-> :request-approval   (:disposition :escalate, interrupt-before)
                                             +-> :hold               (:disposition :hold)
  :request-approval -> :commit                                      (resume = approval)
  ```

  The unconditional invariant (hr-management.governor's own docstring):
  the HRManagementAdvisor can never directly commit an hr-record or
  personnel-action the HRManagementGovernor refuses — EVERY
  record-hr-record!/record-personnel-action! call is gated behind
  :decide, and EVERY disposition this actor reaches (commit or hold) is
  appended to the Store's append-only ledger via
  `hr-management.store/append-ledger!`. `hr-management.governor` and
  `hr-management.store`'s existing hard-invariant/safety-rank logic are
  NOT modified by this ns — both are called through their existing,
  unchanged public API (`governor/env-for-store`, `governor/assess`,
  `store/record-hr-record!`, `store/record-personnel-action!`,
  `store/append-ledger!`)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [hr-management.advisor :as advisor]
            [hr-management.governor :as governor]
            [hr-management.phase :as phase]
            [hr-management.store :as store]))

;; ----------------------------- store adapter -----------------------------

(defn- commit-record!
  "Dispatch a governor-cleared (and phase-cleared) proposal to the ONE
  matching EXISTING `hr-management.store` write fn for its :action —
  never a generic/new store method, never a raw store mutation outside
  this call."
  [st {:keys [action employee-id note action-type urgent?]}]
  (case action
    :personnel-action
    (let [record {:action-id (str "pa-" employee-id "-" (gensym))
                  :employee-id employee-id
                  :action-type action-type
                  ;; only ever reached via :request-approval (personnel
                  ;; actions always escalate — governor.cljc SOFT 4), so
                  ;; a human, not a kiosk, executed it.
                  :executed-by :hr-manager}]
      (store/record-personnel-action! st record)
      record)

    (let [record {:record-id (str "hr-" employee-id "-" (gensym))
                  :employee-id employee-id
                  :note note
                  :urgent? (boolean urgent?)}]
      (store/record-hr-record! st record)
      record)))

;; ----------------------------- nodes -----------------------------

(defn- decide-node
  [{:keys [proposal verdict context]}]
  (let [base (phase/verdict->disposition verdict)
        ph (:phase context phase/default-phase)
        {:keys [disposition reason]} (phase/gate ph proposal base)]
    {:disposition disposition
     :audit [(cond-> {:node :decide :decision (:decision verdict)
                       :disposition disposition :phase ph}
               reason (assoc :phase-reason reason)
               (seq (:violations verdict)) (assoc :violations (:violations verdict)))]}))

(defn- commit-node [st {:keys [proposal]}]
  (let [record (commit-record! st proposal)]
    (store/append-ledger! st {:disposition :commit :proposal proposal :record record})
    {:record record
     :audit [{:node :commit :record record}]}))

(defn- hold-node [st {:keys [proposal verdict]}]
  (store/append-ledger! st {:disposition :hold :proposal proposal :verdict verdict})
  {:audit [{:node :hold :verdict verdict}]})

;; ----------------------------- build -----------------------------

(defn build-graph
  "Build a compiled HRManagementActor graph. `store` implements
  `hr-management.store/Store`. `advisor` implements
  `hr-management.advisor/Advisor` (defaults to `mock-advisor`).
  `checkpointer` defaults to an in-memory one."
  [{:keys [store advisor checkpointer]
    :or {advisor (advisor/mock-advisor)
         checkpointer (cp/mem-checkpointer)}}]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :audit       {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :advise
                   (fn [{:keys [request]}]
                     (let [p (advisor/-advise advisor store request)]
                       {:proposal p
                        :audit [{:node :advise :request request :proposal p}]})))
      (g/add-node :govern
                   (fn [{:keys [proposal]}]
                     (let [v (governor/assess (governor/env-for-store store) proposal)]
                       {:verdict v
                        :audit [{:node :govern :verdict v}]})))
      (g/add-node :decide decide-node)
      (g/add-node :request-approval (fn [s] s))
      (g/add-node :commit (partial commit-node store))
      (g/add-node :hold (partial hold-node store))
      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges
       :decide
       (fn [{:keys [disposition]}]
         (case disposition
           :commit :commit
           :escalate :request-approval
           :hold)))
      (g/add-edge :request-approval :commit)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                         :interrupt-before #{:request-approval}})))

;; ----------------------------- run -----------------------------

(defn run-request!
  "Run one HR operation request (e.g. {:employee-id .. :action
  :hr-record :note ..}) through the compiled actor graph to completion
  or interrupt. `context` may carry `:phase` (defaults to
  `hr-management.phase/default-phase`, 3). `thread-id` scopes
  checkpointing for resume after human approval."
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: the interrupted `:request-approval` node
  advances straight to `:commit` on resume (approval is the act of
  resuming the thread — mirrors cloud-itonami-isco-1213's
  policyplan.actor/approve!)."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
