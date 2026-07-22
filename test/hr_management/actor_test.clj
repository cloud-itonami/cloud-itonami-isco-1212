(ns hr-management.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [hr-management.actor :as actor]
            [hr-management.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-employee! st {:employee-id "emp-1" :hired-at "2026-01-01"})
    (store/register-role-mandate! st {:employee-id "emp-1" :position "engineer"})
    st))

(deftest commits-a-clean-routine-hr-record
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:employee-id "emp-1" :action :hr-record :note "quarterly check-in"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (= :commit (get-in result [:state :disposition])))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/hr-records-of st "emp-1"))))
    (is (= 1 (count (store/ledger st))))
    (is (= :commit (:disposition (first (store/ledger st)))))))

(deftest governor-rejection-blocks-commit-end-to-end
  (testing "a HARD violation (unregistered employee) holds -- it never
            reaches record-hr-record!, and it's still audited"
    (let [st (store/mem-store) ;; nobody registered
          _ (is (empty? (store/ledger st)) "ledger starts empty")
          graph (actor/build-graph {:store st})
          request {:employee-id "ghost" :action :hr-record :note "note"}
          result (actor/run-request! graph request {} "thread-2")]
      (is (= :done (:status result)))
      (is (= :hold (get-in result [:state :disposition])))
      (is (empty? (store/hr-records-of st "ghost")))
      (is (= 1 (count (store/ledger st))))
      (let [fact (first (store/ledger st))]
        (is (= :hold (:disposition fact)))
        (is (some #(= :no-employee-record (:rule %)) (:violations (:verdict fact))))))))

(deftest holds-until-approved-then-commits-a-personnel-action
  (testing "the ledger stays EMPTY while interrupted -- it is only ever
            populated once :commit or :hold actually runs, never at
            build/advise/govern time"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:employee-id "emp-1" :action :personnel-action :action-type :transfer}
          interrupted (actor/run-request! graph request {} "thread-3")]
      (is (= :interrupted (:status interrupted)))
      (is (= [:request-approval] (:frontier interrupted)))
      (is (empty? (store/personnel-actions-of st "emp-1")))
      (is (empty? (store/ledger st)) "no ledger entry yet -- still parked at request-approval")
      (let [resumed (actor/approve! graph "thread-3")]
        (is (= :done (:status resumed)))
        (is (= 1 (count (store/personnel-actions-of st "emp-1"))))
        (is (= 1 (count (store/ledger st))))
        (is (= :commit (:disposition (first (store/ledger st)))))
        (is (= :hr-manager (:executed-by (first (store/personnel-actions-of st "emp-1")))))))))

(deftest urgent-complaint-always-escalates-end-to-end-even-at-phase-3
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:employee-id "emp-1" :action :hr-record :note "harassment complaint"
                  :urgent? true}
        interrupted (actor/run-request! graph request {:phase 3} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/hr-records-of st "emp-1")))))

(deftest phase-0-read-only-holds-even-a-governor-clean-proposal
  (testing "the phase gate can make a disposition MORE conservative than
            the governor's own verdict -- a governor-:proceed proposal
            still holds at phase 0"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:employee-id "emp-1" :action :hr-record :note "note"}
          result (actor/run-request! graph request {:phase 0} "thread-5")]
      (is (= :done (:status result)))
      (is (= :hold (get-in result [:state :disposition])))
      (is (empty? (store/hr-records-of st "emp-1")))
      (let [decide-audit (first (filter #(= :decide (:node %)) (get-in result [:state :audit])))]
        (is (= :phase-disabled (:phase-reason decide-audit)))))))

(deftest phase-1-assisted-log-requires-approval-even-when-governor-clean
  (testing "phase 1 enables the :hr-record WRITE but not AUTO-commit --
            a clean proposal still parks at request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:employee-id "emp-1" :action :hr-record :note "note"}
          interrupted (actor/run-request! graph request {:phase 1} "thread-6")]
      (is (= :interrupted (:status interrupted)))
      (is (empty? (store/hr-records-of st "emp-1")))
      (let [resumed (actor/approve! graph "thread-6")]
        (is (= :done (:status resumed)))
        (is (= 1 (count (store/hr-records-of st "emp-1"))))))))
