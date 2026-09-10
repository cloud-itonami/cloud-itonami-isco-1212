(ns hr-management.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [hr-management.store :as store]
            [hr-management.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-employee! st {:employee-id "emp-1" :hired-at "2026-01-01"})
    (store/register-role-mandate! st {:employee-id "emp-1" :position "engineer"})
    st))

(deftest proceeds-on-clean-routine-hr-record
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "emp-1" :safety-class :low
                   :effect :propose :confidence 0.9}]
    (is (= :proceed (:decision (governor/assess env proposal))))))

(deftest holds-on-unregistered-role-mandate
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "no-such-employee" :safety-class :low
                   :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-role-mandate (:rule %)) (:violations result)))))

(deftest holds-on-orphaned-role-mandate-with-no-employee-record
  ;; register-employee!/register-role-mandate! are two independent Store
  ;; writes with no atomic combined operation -- a role-mandate can exist
  ;; for an employee-id that was never registered (or whose employee
  ;; record was independently removed/never landed). hard-violations used
  ;; to check ONLY role-mandate-fn, so this orphaned state slipped through
  ;; as a clean pass. Same gap already found and fixed in the platform
  ;; edge copy (cloud-itonami.edge.hr-governor).
  (let [st (store/mem-store)
        _ (store/register-role-mandate! st {:employee-id "orphan-emp" :position "engineer"})
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "orphan-emp" :safety-class :low
                   :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-employee-record (:rule %)) (:violations result)))))

(deftest holds-on-no-actuation-violation
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "emp-1" :safety-class :low
                   :effect :direct-write :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-actuation (:rule %)) (:violations result)))))

(deftest urgent-complaint-always-escalates-even-when-clean-and-confident
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "emp-1" :safety-class :none
                   :effect :propose :confidence 1.0 :urgent? true}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :urgent-complaint (:reason result)))))

(deftest personnel-action-always-escalates
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :personnel-action :employee-id "emp-1" :safety-class :none
                   :effect :propose :confidence 1.0}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :personnel-action (:reason result)))))

(deftest holds-on-unrecognized-safety-class-instead-of-silently-proceeding
  ;; safety-rank used .indexOf, which returns -1 (not an exception) for a
  ;; value outside safety-classes, silently ranked as 0 == :none -- an
  ;; unrecognized safety-class (typo, wrong type, or unexpected Advisor
  ;; output) used to bypass the mandatory human-approval gate for
  ;; :high/:safety-critical proposals instead of failing closed.
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "emp-1" :safety-class :extreme
                   :effect :propose :confidence 1.0}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :invalid-safety-class (:rule %)) (:violations result)))))

(deftest human-approval-on-high-safety-class-even-when-clean
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "emp-1" :safety-class :high
                   :effect :propose :confidence 0.9}]
    (is (= :human-approval (:decision (governor/assess env proposal))))))

(deftest human-approval-on-low-confidence
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :hr-record :employee-id "emp-1" :safety-class :none
                   :effect :propose :confidence 0.2}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :low-confidence (:reason result)))))

(deftest store-records-append-only
  (let [st (fresh-store)]
    (store/record-hr-record! st {:record-id "r1" :employee-id "emp-1" :note {:rating :meets}})
    (store/record-personnel-action! st {:action-id "a1" :employee-id "emp-1" :action-type :transfer})
    (is (= 1 (count (store/hr-records-of st "emp-1"))))
    (is (= 1 (count (store/personnel-actions-of st "emp-1"))))
    (is (empty? (store/hr-records-of st "emp-2")))))

(deftest a-proposal-without-confidence-does-not-proceed
  (testing "確信度を言っていない提案は、確信していると言っていないので auto-proceed
            させない。この既定は 2026-07-30 まで 1.0 で、:confidence を持たない提案が
            :proceed していた（ADR-2607309100）。fleet の boolean 方言 346 件はすべて
            0.0 既定で、うち isco-5419 はそれを明示的にテストしている。"
    (let [st (fresh-store)
          env (governor/env-for-store st)
          proposal {:action :hr-record :employee-id "emp-1" :safety-class :low :effect :propose}
          result (governor/assess env proposal)]
      (is (= 0.0 (:confidence result))
          "欠落した :confidence は 0.0 であって 1.0 ではない")
      (is (not= :proceed (:decision result))
          "確信度不明の提案が自動で通ってはならない"))))
