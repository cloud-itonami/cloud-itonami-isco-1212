(ns hr-management.governor
  "HRManagementGovernor — the independent compliance/traceability layer for
  the ISCO-08 1212 independent HR-management actor. The HR Advisor proposes
  actions (log a review note, initiate a personnel action); it has no
  notion of role-mandate provenance or urgent-complaint escalation, so this
  MUST be a separate system able to *reject* a proposal and fall back to
  HOLD — the itonami-actor pattern (independent Governor gates a proposing
  actor) applied to this occupation.

  Charter (mirrors ADR-2607011000 robotics premise + ADR-2607012000
  cloud-itonami-isco): the actor never executes a personnel action or
  writes an operating record the governor refuses. Compensation changes,
  transfers and terminations, and any urgent complaint or safety-incident
  report, ALWAYS require human HR-manager sign-off, even when every hard
  invariant passes — the itonami analog of a no-diagnosis-by-LLM charter.

  HARD invariants for :hr/propose:
    1. Role-mandate provenance — an hr-record or personnel-action event
       must reference a registered employee with a registered role
       mandate. Checks BOTH halves independently (:no-employee-record /
       :no-role-mandate) — register-employee!/register-role-mandate! are
       two independent Store writes with no atomic combined operation, so
       a role-mandate can be registered for an employee-id that was never
       (or no longer) registered; checking only role-mandate-fn would let
       that orphaned state slip through as a clean pass even though this
       docstring already claimed the employee half was enforced too. Same
       gap already found and fixed in the platform edge copy
       (cloud-itonami.edge.hr-governor, gftdcojp/cloud-itonami).
    2. No-actuation         — the proposal must not directly mutate an
       hr-record or personnel-action outside the
       record-hr-record!/record-personnel-action! path (effect must be
       :propose, never a raw store write).
    3. Urgent-complaint escalation — a proposal flagged `:urgent? true`
       (harassment complaint, safety incident, whistleblower report) can
       never be `:proceed`; it is always routed to `:human-approval`,
       regardless of safety-class or confidence. This cannot be
       suppressed.
  SOFT:
    4. Personnel-action always escalates to human sign-off (no autonomous
       compensation change, transfer or termination, kiosk or otherwise).
    5. Confidence floor → escalate."
  (:require [hr-management.store :as store]))

(def confidence-floor 0.6)
(def safety-classes [:none :low :medium :high :safety-critical])
(def ^:private known-safety-classes (set safety-classes))

;; .indexOf returns -1 (not an exception) for a safety-class outside
;; safety-classes, and this silently mapped to rank 0 == :none -- an
;; unrecognized safety-class (a typo, wrong type, or an unexpected value
;; from a buggy/malicious Advisor) used to silently rank as the LEAST
;; severe class, defeating `(>= (safety-rank safety-class) (safety-rank
;; :high))` in `assess` below and letting it bypass the mandatory
;; human-approval gate instead of failing closed. Callers must reject an
;; unrecognized safety-class as a hard violation (see hard-violations)
;; before it ever reaches this rank comparison.
(defn- safety-rank [safety-class]
  (let [idx (.indexOf safety-classes safety-class)]
    (if (neg? idx) 0 idx)))

(defn- hard-violations [{:keys [employee-fn role-mandate-fn]} proposal]
  (let [{:keys [employee-id effect safety-class]} proposal
        employee (employee-fn employee-id)
        role-mandate (role-mandate-fn employee-id)]
    (cond-> []
      (nil? employee)
      (conj {:rule :no-employee-record :detail (str "未登録従業員 " employee-id)})

      (nil? role-mandate)
      (conj {:rule :no-role-mandate :detail (str "未登録 role-mandate " employee-id)})

      (not= :propose effect)
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and (some? safety-class) (not (contains? known-safety-classes safety-class)))
      (conj {:rule :invalid-safety-class
             :detail (str "未知の safety-class " safety-class)}))))

(defn assess
  "Assess a proposal against `env` (a map with `:employee-fn`/
  `:role-mandate-fn` lookups, decoupled from any concrete Store so this
  stays pure). Returns
  `{:decision :proceed|:hold|:human-approval :violations [...] :confidence n}`."
  [env proposal]
  (let [violations (hard-violations env proposal)
        safety-class (or (:safety-class proposal) :none)
        confidence (or (:confidence proposal) 0.0)
        urgent? (boolean (:urgent? proposal))
        personnel-action? (= :personnel-action (:action proposal))]
    (cond
      (seq violations)
      {:decision :hold :violations violations :confidence confidence}

      urgent?
      {:decision :human-approval :violations [] :confidence confidence
       :reason :urgent-complaint}

      personnel-action?
      {:decision :human-approval :violations [] :confidence confidence
       :reason :personnel-action}

      (>= (safety-rank safety-class) (safety-rank :high))
      {:decision :human-approval :violations [] :confidence confidence}

      (< confidence confidence-floor)
      {:decision :human-approval :violations [] :confidence confidence
       :reason :low-confidence}

      :else
      {:decision :proceed :violations [] :confidence confidence})))

(defn env-for-store
  "Build the decoupled env map `assess` needs from a concrete
  `hr-management.store/Store` implementation."
  [store]
  {:employee-fn #(store/employee store %)
   :role-mandate-fn #(store/role-mandate-of store %)})
