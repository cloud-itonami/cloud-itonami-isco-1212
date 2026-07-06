# Stripe Billing Setup (live mode, Gftd Japan株式会社 account)

This is a manual Dashboard runbook, not automated, because the API key
connected to this session has `PostProducts` but not `PostPrices` write
permission, and Stripe's Billing Meters API is not exposed by the current
tool integration. Run this once against the live account
(`acct_1AFjSQLGWYDWP5eY`, dashboard: https://dashboard.stripe.com/acct_1AFjSQLGWYDWP5eY).

## Pricing basis (and why these numbers, not others)

Neither upstream platform this product resells has a finalized retail
per-unit rate:

- **kotobase.net storage**: a real wholesale number exists but its ADR
  (`2606130100-kotobase-storage-pricing.md`) is status `proposed`, not
  `accepted`, and isn't wired to any billing system yet. Standard tier:
  ¥980/50GiB/mo ≈ **¥19.6/GiB/mo wholesale**.
- **murakumo.cloud LLM tokens**: no $/token cost-basis exists anywhere —
  ADR `2607030030-murakumo-inference-economy-gtm.md` deliberately avoids
  per-token pricing. Its live fiat SKUs (¥1,500/¥7,500/¥30,000 credit packs)
  don't document a credits-per-yen exchange rate, so a ¥/token retail price
  cannot be honestly derived from them today.

Rather than invent a precise-looking ¥/token number with no real basis,
this meters LLM usage at the coarser, auditable unit of **one HR Advisor
proposal call** (one governed LLM invocation), not raw tokens. This can be
switched to per-1K-token billing later with zero code change (just add a
new Price) once murakumo publishes real retail token pricing.

| Meter | Wholesale basis | Retail (with markup) | Status |
|---|---|---|---|
| storage_gb | ¥19.6/GiB/mo (kotobase, proposed) | **¥30/GiB/mo** (~53% markup) | real number, marked-up |
| llm_proposal | unknown (no murakumo retail token rate exists) | **¥10/proposal** | placeholder — revisit when murakumo Phase 2 pricing lands |

Both numbers are trivially editable in the Dashboard after creation — no
code depends on the specific value, only on the Price ID (read from env).

## Steps

1. **Product** — Products → Add product
   - Name: `cloud-itonami-isco-1212 — Independent HR Management Practice`
   - Description: `Usage-based HR management practice: LLM-advised HR records + audit-ledger storage, gated by an independent governor. Billed by AI-advisor proposals and storage used.`
   - Metadata: `blueprint_id=cloud-itonami-isco-1212`, `isco_08=1212`

2. **Meter: `hr_management_llm_proposals`** — Billing → Meters → Create meter
   - Event name: `hr_management_llm_proposal`
   - Aggregation: **Sum** (count proposals during the billing period)
   - Value key: `value` (each meter event reports `value: 1` per proposal)
   - Customer key: `stripe_customer_id`

3. **Meter: `hr_management_storage_gb`** — Billing → Meters → Create meter
   - Event name: `hr_management_storage_gb`
   - Aggregation: **Last** (bill the last-reported current-usage snapshot per period, not a sum — storage is a gauge, not a counter)
   - Value key: `value` (each event reports current total GiB stored)
   - Customer key: `stripe_customer_id`

4. **Price: LLM proposals** — attach to the product
   - Model: Usage-based (metered), linked to meter `hr_management_llm_proposals`
   - Unit price: **¥10** per unit (1 unit = 1 proposal)
   - Billing period: monthly
   - Nickname: `llm-proposal-v1-placeholder`

5. **Price: Storage** — attach to the product
   - Model: Usage-based (metered), linked to meter `hr_management_storage_gb`
   - Unit price: **¥30** per unit (1 unit = 1 GiB)
   - Billing period: monthly
   - Nickname: `storage-gb-v1`

6. **Checkout** — no separate setup needed beyond the above; the app creates
   a Checkout Session in `subscription` mode with both Prices as line items
   (quantity omitted/1 for metered prices — Stripe ignores quantity for
   metered lines and starts usage at 0).

7. **Customer Portal** — Settings → Billing → Customer portal → enable
   "View invoice history" and "Update payment method". Subscription
   cancellation should be allowed; plan-switching not needed (single plan).

8. **After creating**, copy these 3 IDs back into the deploy config
   (`wrangler.jsonc` secrets, not committed to git):
   - `STRIPE_PRICE_LLM_PROPOSAL` (price_...)
   - `STRIPE_PRICE_STORAGE_GB` (price_...)
   - `STRIPE_PRODUCT_ID` (prod_...)

No live Checkout link should be shared publicly until end-to-end testing
(register → subscribe → use → see it on an invoice) has been run once with
a real card in live mode by a human (Stripe live mode has no test cards).
