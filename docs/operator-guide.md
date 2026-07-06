# Operator Guide

## First Deployment

1. Define the operator's client roster and intake process.
2. Define consent and purpose categories for personnel data.
3. Run synthetic operating cases.
4. Enable human-reviewed sign-off for `:high`/`:safety-critical` actions.
5. Measure operating outcomes and audit coverage.

## A Day in the Life: intake → propose → approve → execute → audit

This is what the loop looks like for one actual HR-management day, not an
abstract "task." HR manager Sana runs a solo `cloud-itonami-isco-1212`
practice covering three small employers today, plus whatever comes in
urgent.

1. **Intake.** A client employer sends a new-hire's role mandate: position,
   review cadence, and reporting line. `:identity` binds the employee record
   to this practice; `:forms` captures consent + purpose and the
   HR-manager-authored role mandate. No HR record is filed for this employee
   until this exists.
2. **Propose.** During the review cycle, the HR Advisor reads the day's
   `:bpmn`-modeled workflow and proposes the next action: "log Q3 review
   note for Employee 3, rating in-line with role mandate." It never proposes
   a compensation change or termination on its own — that stays with Sana.
3. **Approve.** Before *any* personnel action, the proposal goes to
   `:hr-management-governor`. Routine, in-mandate review notes with consent
   on file clear automatically. A proposed compensation change is
   `:safety-critical` — it holds until Sana, the accountable HR manager,
   signs off *for this action specifically*. This is the equivalent of
   restocking at the depot before a round: the sign-off is good for one
   action, never a standing permit.
4. **Execute.** Only after sign-off does the record get written or the
   personnel action get initiated; `:telemetry` streams engagement/pulse
   signals live. If a signal comes back as an urgent complaint — say a
   harassment report filed mid-cycle — that's an urgent-complaint signal and
   it escalates to Sana immediately; the governor will not let it be
   suppressed or queued for later, no matter what else is in progress.
5. **Audit.** Every governor decision (auto-approved, held, escalated), every
   HR-manager sign-off, and every disclosure (e.g. sending a verification
   letter to a benefits carrier) lands in the `:audit-ledger` with consent +
   purpose attached. This is what a certification reviewer or employer audit
   later inspects (see Certification below).

Skip step 3 — execute a safety-critical personnel action without a
same-action sign-off — and that's a governor violation: the action is
refused, not just logged after the fact.

## Minimum Production Controls

- consent and disclosure log
- safety-critical escalation path
- provenance for all operating records
- human review for high-risk cases
- audit export for all gated actions

## Certification

Certified operators must prove that the governor gates every
safety-critical personnel action, and that urgent complaints and
safety-incident signals escalate to humans.
