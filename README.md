# cloud-itonami-isco-1212

Open Occupation Blueprint for **ISCO-08 1212**: Human Resource Managers.

This repository designs a forkable OSS business for an independent/fractional
HR manager who runs outsourced "HR-as-a-service" for small and mid-size
employers that cannot support a full in-house HR department — a
kaonavi-style org-chart, employee-record and personnel-action practice: an
HR Advisor proposes actions (log a review note, initiate a transfer, flag a
compensation change) and an independent governor gates them before anything
sensitive is executed.

## Robotics premise (adapted)

All cloud-itonami verticals are designed on the premise that a robot
performs the physical domain work. Human Resource Management is primarily a
desk/data occupation, so the physical component here is intentionally
minimal: an onboarding/check-in kiosk robot that issues badges, hands over
welcome kits and guides new hires between orientation stops, under an actor
that proposes actions and an independent **HR Management Governor** that
gates them. The governor never dispatches hardware itself, and — unlike the
manual-labor cloud-itonami verticals where the robot does the core physical
work — the robot here is confined to the onboarding periphery. It is never
the thing that decides or executes a compensation change, transfer or
termination; those stay `:safety-critical`-equivalent actions that always
require a human HR manager's sign-off.

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html) —
pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
employee record + role mandate (position, review cadence)
        |
        v
HR Advisor -> HR Management Governor -> record, hold for HR-manager review, or escalate
        |
        v
personnel actions (gated) + operating records + audit ledger
```

No automated advice can execute a compensation change, transfer or
termination the governor refuses, suppress an urgent complaint or
safety-incident signal, or disclose personnel data without governor approval
and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `1212`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger
- :telemetry

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation

`src/hr_management/{store,governor}.cljc` is a minimal but real
implementation of the Core Contract above (pure cljc, no external deps):

- `hr-management.store` — `Store` protocol + `MemStore`: employees, role
  mandates, HR records, personnel actions. An HR record or personnel-action
  event can only be recorded against a registered employee with a
  registered role mandate (role-mandate provenance).
- `hr-management.governor` — `HRManagementGovernor`: `assess` gates a
  proposal against the role-mandate env. Hard invariants force `:hold` (no
  role mandate, or a direct-write instead of `:propose`); `:urgent? true`
  proposals (harassment complaint, safety incident, whistleblower report)
  and personnel-action proposals (compensation change, transfer,
  termination) **always** escalate to `:human-approval` regardless of
  safety-class or confidence — this cannot be suppressed; `:high`/
  `:safety-critical` and low-confidence proposals also escalate.

```bash
clojure -M:test   # 8 tests, 15 assertions, green
```

## License

AGPL-3.0-or-later.
