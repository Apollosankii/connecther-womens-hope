# Backend contract — ConnectHer mobile v1 (Figma-aligned, no schema change)

## Principle

Mobile apps use **existing** PostgREST RPCs (notably `create_booking_request` with a single `p_proposed_price`). Figma frames that show **multiple menu lines** and **checkout line items** are addressed in clients by **option (1)**:

- **UI** may collect line items and quantities locally.
- **Persistence** sends one `proposed_price` (sum) and may append a readable **Quote:** breakdown to `p_message` (see `BookingQuoteAggregator` on Android and `appendQuoteBreakdown` in Expo `src/utils/bookingQuote.ts`).

## When to extend the backend

Move to **option (2)** (new tables / RPCs / Paystack metadata) only when product requires:

- Stored line items per booking request, or  
- Distinct **deposit** state machine separate from today’s Paystack subscription / checkout flows.

Then ship **Supabase migration + RLS + Kotlin `SupabaseData` + Expo `services/api` + admin portal** in one coordinated change set.

## Deploy / admin

- Apply migrations via Supabase CLI or dashboard as today.  
- If schema changes, update [connecther-admin-portal](../../connecther-admin-portal) SQL templates and `supabase_data.py` in the same release.
