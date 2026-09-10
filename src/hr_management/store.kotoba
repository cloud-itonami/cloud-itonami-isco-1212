(ns hr-management.store
  "SSoT for the ISCO-08 1212 independent HR-management sole-proprietor
  actor, behind a `Store` protocol so the backend is a swap (MemStore
  default ‖ a real Datomic/kotoba-server backend, per the itonami actor
  pattern).

  Domain = independent/fractional HR management practice:

    employee         — a person under HR management (employeeId, hiredAt)
    role-mandate      — the employment record authorizing HR actions for an
                        employee (mandateId, employeeId, position,
                        requiresHrReview?)
    hr-record         — an appended HR event: review note, 1-on-1 summary
                        (recordId, employeeId, note, urgent? boolean)
    personnel-action   — a sensitive personnel-action event: transfer,
                        compensation change, termination (actionId,
                        employeeId, actionType, executedBy #{:kiosk
                        :hr-manager})

  The append-only records are the operating ledger: an hr-record or
  personnel-action event must reference a registered employee with a
  registered role mandate, and records are never mutated in place, only
  appended.

  `ledger`/`append-ledger!` (added for the hr-management.actor
  StateGraph, modeled on cloud-itonami-isco-1213's policyplan.store /
  cloud-itonami-isco-4311's bookkeeping.store): a SEPARATE append-only
  audit trail of every :commit/:hold disposition the actor reaches,
  regardless of outcome — distinct from hr-records-of/
  personnel-actions-of, which hold only the committed domain records
  themselves. Additive only; does not change any existing Store method
  or MemStore field above.")

(defprotocol Store
  (employee [st employee-id])
  (role-mandate-of [st employee-id])
  (hr-records-of [st employee-id])
  (personnel-actions-of [st employee-id])
  (register-employee! [st employee])
  (register-role-mandate! [st role-mandate])
  (record-hr-record! [st hr-record])
  (record-personnel-action! [st personnel-action])
  (ledger [st])
  (append-ledger! [st fact]))

(defrecord MemStore [state]
  Store
  (employee [_ employee-id]
    (get-in @state [:employees employee-id]))
  (role-mandate-of [_ employee-id]
    (get-in @state [:role-mandates employee-id]))
  (hr-records-of [_ employee-id]
    (filter #(= employee-id (:employee-id %)) (:hr-records @state)))
  (personnel-actions-of [_ employee-id]
    (filter #(= employee-id (:employee-id %)) (:personnel-actions @state)))
  (register-employee! [_ employee]
    (swap! state assoc-in [:employees (:employee-id employee)] employee))
  (register-role-mandate! [_ role-mandate]
    (swap! state assoc-in [:role-mandates (:employee-id role-mandate)] role-mandate))
  (record-hr-record! [_ hr-record]
    (swap! state update :hr-records (fnil conj []) hr-record))
  (record-personnel-action! [_ personnel-action]
    (swap! state update :personnel-actions (fnil conj []) personnel-action))
  (ledger [_]
    (:ledger @state))
  (append-ledger! [_ fact]
    (swap! state update :ledger (fnil conj []) fact)))

(defn mem-store
  ([] (mem-store {}))
  ([seed]
   (->MemStore (atom (merge {:employees {} :role-mandates {} :hr-records []
                              :personnel-actions [] :ledger []}
                            seed)))))
