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
  appended.")

(defprotocol Store
  (employee [st employee-id])
  (role-mandate-of [st employee-id])
  (hr-records-of [st employee-id])
  (personnel-actions-of [st employee-id])
  (register-employee! [st employee])
  (register-role-mandate! [st role-mandate])
  (record-hr-record! [st hr-record])
  (record-personnel-action! [st personnel-action]))

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
    (swap! state update :personnel-actions (fnil conj []) personnel-action)))

(defn mem-store
  ([] (mem-store {}))
  ([seed]
   (->MemStore (atom (merge {:employees {} :role-mandates {} :hr-records [] :personnel-actions []} seed)))))
