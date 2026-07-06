# Business Model: Independent HR Management Practice

## Classification

- Repository: `cloud-itonami-isco-1212`
- ISCO-08: `1212`
- Occupation: Human Resource Managers
- Social impact: fair-employment, worker-privacy, sme-hr-access

## Customer

- small/mid-size employers without a full in-house HR department
- individual employees whose records this practice manages on the
  employer's behalf
- payroll and benefits providers integrating downstream

## Offer

- employee-record and org-chart management (kaonavi-style)
- review-cycle and 1-on-1 documentation
- personnel-action handling: transfers, compensation changes, terminations
- urgent-complaint and safety-incident escalation
- onboarding (badge issuance, orientation routing via kiosk robot)

## Revenue

- per-employee monthly management fee
- one-time onboarding/offboarding fee
- personnel-action advisory fee (transfer, comp-change, termination review)

## Trust Controls

- no compensation change, transfer or termination executed by the LLM
- urgent-complaint escalation cannot be suppressed
- personnel actions near legal exposure require an HR manager's sign-off
- personnel-data disclosure requires consent and purpose

## `:hr-management-governor` — the decision rule

The governor is the independent checkpoint between the HR Advisor's
*proposal* and any personnel action, disclosure, or record write. It never
executes anything itself (see the Core Contract in the README); it only
approves, holds for HR-manager review, or escalates. Concretely, for
`cloud-itonami-isco-1212` (Independent HR Management Practice) it applies:

**Approves** — a proposed action if all of:
- it is against an employee with a registered role mandate (position, review
  cadence) and within that mandate's scope (e.g. a routine review note, a
  scheduled 1-on-1 summary),
- consent + purpose are on file for the personnel data the action touches, and
- for `:high`/`:safety-critical` steps (compensation change, transfer,
  termination) the practice's licensed/designated HR manager has signed off
  *for that specific action* — a standing blanket approval does not count.

**Rejects / holds**:
- any compensation change, transfer or termination the LLM/HR Advisor
  proposes on its own — personnel-decision authority stays with the human
  HR manager, never the model (`:business/hr-management` is a
  legal-exposure-bearing domain, not a general chat assistant),
- any personnel action attempted without a same-action HR-manager sign-off,
- any personnel-data disclosure (to a payroll provider, benefits carrier, or
  a third party) that lacks matching consent + stated purpose.

**Cannot be suppressed** — an urgent-complaint signal (harassment report,
safety incident, whistleblower disclosure) always escalates to a human, even
if that means overriding an in-progress, already-approved action.

This rule is a direct read of the blueprint's `:social-impact` tags:
- **`:worker-privacy`** — the reject/hold branch: nothing sensitive moves
  without a same-action human sign-off, and escalation can't be silenced.
- **`:fair-employment`** — the reject branch on autonomous personnel
  decisions: compensation, transfer and termination calls never clear
  without a human accountable for the fairness of that specific decision.
- **`:sme-hr-access`** — the approve branch: routine, in-mandate,
  already-consented HR record-keeping clears the governor without manual
  friction, so a single fractional HR manager can actually serve many small
  employers at volume instead of every record waiting on a human.

## Required Technologies — what each is for in this business

`blueprint.edn`'s `:required-technologies` resolve via
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation) for
ISCO-08 `1212`. In this specific practice each one has a concrete job:

- **`:robotics`** — the onboarding/check-in kiosk robot that performs the
  minimal physical work of this desk occupation (badge issuance, welcome-kit
  handoff, orientation routing) strictly under actor-propose /
  governor-gate control; it never touches a personnel decision.
- **`:identity`** — binds the employee record to *this* employee and binds
  each proposed action to *this* HR manager's credentials, so sign-off and
  disclosure both have a verifiable, non-forgeable actor behind them.
- **`:forms`** — captures intake (role mandate: position, review cadence),
  review-cycle notes and personnel-action requests as structured records the
  governor and auditor can actually evaluate, not free text.
- **`:dmn`** — encodes the hr-management-governor's approve/hold/escalate
  rules above as decision tables (e.g. which action types are always
  human-approval), so the rule is inspectable and testable rather than
  buried in prompt text.
- **`:bpmn`** — models the intake → propose → approve → execute → audit
  personnel workflow as an executable process: onboarding, the review
  cadence, and the sequencing of any personnel action.
- **`:audit-ledger`** — the append-only record of every governor decision,
  HR-manager sign-off, and data disclosure for this practice — what a
  certification review or employer audit actually inspects (see
  Certification in `docs/operator-guide.md`).
- **`:telemetry`** — the ongoing pulse/engagement-signal feed (kaonavi-style)
  that feeds the urgent-complaint escalation path; this is what makes
  "urgent-complaint escalation cannot be suppressed" enforceable in near
  real time rather than only at review-cycle time.
