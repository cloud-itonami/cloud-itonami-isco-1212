(ns hr-management.advisor
  "HRManagementAdvisor — proposes ONE HR action for a registered
  employee: either logging a routine hr-record (review note, 1-on-1
  summary), or initiating a personnel-action (transfer, compensation
  change, termination). Swappable mock/llm; the advisor ONLY proposes
  — `hr-management.governor/assess` independently re-checks employee/
  role-mandate provenance, the :propose-only actuation invariant,
  urgent-complaint escalation, personnel-action sign-off and the
  safety-class/confidence floor, and `hr-management.phase/gate` applies
  the rollout-phase write/auto-commit gate on top. Modeled on
  cloud-itonami-isco-1213's policyplan.advisor /
  cloud-itonami-isco-4311's bookkeeping.advisor.

  A proposal is the vocabulary `hr-management.governor/assess` actually
  checks (:action/:employee-id/:effect/:safety-class/:confidence/
  :urgent?, per governor.cljc), never a committed hr-record or
  personnel-action:

    {:action :hr-record|:personnel-action
     :employee-id str
     :effect :propose
     :note str                        ; :hr-record only
     :action-type kw                  ; :personnel-action only,
                                       ; :transfer|:compensation-change
                                       ; |:termination
     :urgent? bool                    ; harassment/safety-incident/
                                       ; whistleblower flag — NEVER
                                       ; suppressible by the advisor's
                                       ; own confidence or rationale
     :safety-class :none|:low|:medium|:high|:safety-critical
     :confidence n
     :stake :low|:medium|:high
     :rationale str}"
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  [_store {:keys [employee-id action note action-type urgent? safety-class stake]}]
  (let [action (or action :hr-record)
        stake (or stake :low)]
    (cond-> {:action action
             :employee-id employee-id
             :effect :propose
             :urgent? (boolean urgent?)
             :safety-class (or safety-class :none)
             :stake stake
             :confidence (case stake :high 0.7 :medium 0.85 0.95)
             :rationale (str "proposed " (name action) " for employee " employee-id)}
      (= action :hr-record) (assoc :note (or note ""))
      (= action :personnel-action) (assoc :action-type action-type))))

(defn mock-advisor
  "Deterministic mock: turns a request straight into a structural
  proposal (no LLM call, no domain-fact fabrication — the :rationale is
  generic/structural, never an invented claim about the employee)."
  []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an HR management advisor. Given a request, propose an
   :action (:hr-record or :personnel-action), the :employee-id, and for
   :hr-record a :note (or for :personnel-action an :action-type), an
   honest :confidence, a :safety-class and a :stake. Set :urgent? true
   ONLY for a genuine harassment complaint / safety incident /
   whistleblower report — never suppress it to make a proposal look
   cleaner. Never step outside the registered employee/role-mandate —
   `hr-management.governor` independently checks provenance and
   escalation regardless of what you propose.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        ;; unparseable output fails CLOSED: :safety-class :high +
        ;; :urgent? true forces human-approval in governor/assess
        ;; rather than silently defaulting to a low-risk :proceed.
        {:action :unknown :effect :propose :confidence 0.0 :stake :high
         :safety-class :high :urgent? true
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:action :unknown :effect :propose :confidence 0.0 :stake :high
       :safety-class :high :urgent? true
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  "Real-LLM Advisor: `model-generate-fn` is a `(chat-model msgs opts) ->
  {:content str}`-shaped fn (same injection shape as
  cloud-itonami-isco-1213's policyplan.advisor/llm-advisor). Still only
  ever returns a :propose proposal — never writes to `store`."
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "HR request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
