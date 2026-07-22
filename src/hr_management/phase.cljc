(ns hr-management.phase
  "Phase 0->3 staged rollout for the ISCO-08 1212 independent
  HR-management actor (mirrors `plumbing.phase` [cloud-itonami-isco-7126]
  and `manufacturing-floor.phase` [cloud-itonami-isco-1321]).

    Phase 0  read-only        -- no writes at all; any proposal that
                                  reaches :decide holds on
                                  :phase-disabled, regardless of
                                  `hr-management.governor/assess`'s own
                                  verdict.
    Phase 1  assisted-log     -- :hr-record writes allowed, but every
                                  commit-eligible proposal STILL needs
                                  human approval (nothing auto-commits
                                  yet).
    Phase 2  assisted-log     -- identical writes/auto set to phase 1;
                                  reserved as a rollout checkpoint before
                                  phase 3 turns autonomy on (same staged
                                  posture as `plumbing.phase`).
    Phase 3  supervised-auto  -- governor-clean, high-confidence
                                  :hr-record proposals may auto-commit.

  :personnel-action is deliberately ABSENT from every phase's :auto set,
  including phase 3 -- but this omission is belt-and-suspenders
  documentation of a fact `hr-management.governor/assess` ALREADY
  guarantees one layer down: a `:personnel-action` proposal (transfer,
  compensation change, termination) or a proposal flagged `:urgent?
  true` (harassment complaint, safety incident, whistleblower report)
  unconditionally returns `:decision :human-approval` from `assess`
  itself, before this phase gate ever runs (see governor.cljc's own
  docstring, SOFT invariants 3-4). So the two independent layers agree:
  no HR actor kiosk ever executes a personnel action or an
  urgent-complaint disposition on its own, at any phase -- exactly the
  same two-independent-layers shape as `manufacturing-floor.phase`'s
  `:clear-fail` note."
  )

(def read-ops #{})
(def write-ops #{:hr-record :personnel-action})

(def phases
  "phase -> {:label .. :writes <actions allowed to write> :auto <actions
  allowed to auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                :auto #{}}
   1 {:label "assisted-log"    :writes #{:hr-record}       :auto #{}}
   2 {:label "assisted-log"    :writes #{:hr-record}       :auto #{}}
   3 {:label "supervised-auto" :writes write-ops           :auto #{:hr-record}}})

(def default-phase 3)

(defn gate
  "Adjust a base disposition (:commit/:escalate/:hold, from
  `verdict->disposition` below) for the rollout phase. Returns
  {:disposition kw :reason kw|nil}. A :hold base disposition is NEVER
  softened by the phase -- a HARD governor violation, or the governor's
  own unconditional human-approval routing (already folded into
  `verdict->disposition` as :escalate before this fn runs), always
  wins; this fn can only ever make a disposition MORE conservative
  (:commit -> :escalate, or anything -> :hold when the action's write
  isn't enabled for the phase), never less."
  [phase {:keys [action]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)     {:disposition :hold :reason nil}
      (contains? read-ops action)        {:disposition governor-disposition :reason nil}
      (not (contains? writes action))    {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto action))) {:disposition :escalate :reason :phase-approval}
      :else                              {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Maps `hr-management.governor/assess`'s OWN `:decision` (:proceed/
  :hold/:human-approval) to a base disposition (:commit/:hold/:escalate)
  BEFORE the phase gate runs. `hr-management.governor/assess` is NOT
  modified by this ns -- this fn only adapts its existing, unmodified
  return shape."
  [verdict]
  (case (:decision verdict)
    :proceed        :commit
    :human-approval :escalate
    :hold))
