# ConnectHer — Figma reference ↔ mobile apps matrix

**Strategy:** Evolve existing screens; Figma is a checklist, not a pixel replacement.  
**Figma file:** [Connecther](https://www.figma.com/design/kSvTvj1QCNO5Xcj6LVMQsz/Connecther?node-id=0-1) · `fileKey` **`kSvTvj1QCNO5Xcj6LVMQsz`**

## Figma MCP — resolved workflow frames (Page `0:1`)

| Frame (canvas name) | `nodeId` | Maps to |
|---------------------|----------|---------|
| Main Page | `24:90` | Home tab / `HomeScreen` — **exclude** from workflow parity passes (layout differs: tabs vs single scroll). |
| Home Cleaning (service menu) | `87:204` | `ServiceMenuActivity` / `AllServicesScreen` / booking entry |
| Caregiving | `2012:265` | Same service-menu pattern |
| Corporate Services | `2022:310` | Same pattern + add-ons / time row |
| Checkout | `2023:141` | `RequestBookingActivity` / `RequestBookingScreen` |
| Profile (seeker account) | `67:1015` | `ProfileScreen` / `SettingsActivity` row order |
| Chatbox | `244:238` | `ChatActivity` / `ChatScreen` |
| **Connection success** *(file layer named “Corporate Services”)* | **`2075:235`** | `ConnectionSuccessActivity` / `ConnectionSuccessScreen` — headline, avatar ring `#BF43A4`, gradient CTA “Send message”, soft bg |
| **Post-job rating** *(file layer named “Corporate Services”)* | **`2075:164`** | `JobRatingScreen` / `RatingActivity` — stars + “Submit Review” |
| **Suggested provider** | *(no standalone frame; use Checkout + service cards)* | `ProviderRecommendationActivity` — provider card + action stack |

**Note:** Duplicate frame names in Figma (“Corporate Services”) differ by **nodeId** and **y-position** on canvas; use IDs above, not titles alone.

Short-lived MCP asset URLs are not stored here. Re-run **user-Figma** / **plugin-figma-figma** `get_screenshot` or `get_design_context` with `fileKey` + `nodeId` when you need fresh exports.

---

## Expo — screen ↔ Figma

| Figma frame | Expo target | Adopted in code | Deferred |
|-------------|-------------|-----------------|----------|
| Main Page `24:90` | `HomeScreen.tsx` | Greeting + search placeholder | Tabs vs single-scroll |
| Home Cleaning / Caregiving / Corporate | `AllServicesScreen`, `RequestBookingScreen`, `CategoryUsersScreen` | Quote breakdown; subtitles; CTAs | Pay deposit split; persisted line items |
| Checkout `2023:141` | `RequestBookingScreen` | Order process, order details card, GPS line, Make request CTA | Map, schedule/monthly |
| Profile `67:1015` | `ProfileScreen.tsx` | Row order: notifications → profile → terms → report → password… | — |
| Chatbox `244:238` | `ChatScreen.tsx` | Header `#EBF0F0` + bottom radius 20; thread `#F5EEEC`; composer `#EF00CF`; incoming border / outgoing fill | Attachment APIs |
| Connection `2075:235` | `ConnectionSuccessScreen.tsx` | 16sp medium headline; avatar ring `#BF43A4`; rings tint | — |
| Rating `2075:164` | `JobRatingScreen.tsx` | Align copy + CTA when touching this flow | — |

---

## Kotlin — screen ↔ Figma + `SupabaseData`

| Figma frame | Kotlin entry | Adopted / simplified | Deferred |
|-------------|--------------|----------------------|----------|
| Main Page | `HomeFragment` | Same as Expo | — |
| Service / booking | `RequestBookingActivity` | Order process card, GPS, docked CTA, `connecther_soft_bg` | Map, schedule |
| Chat `244:238` | `ChatActivity` | `bg_chat_header_bar` (rounded bottom 20dp); thread + composer colors match Figma | Attachments |
| Profile | `SettingsActivity` / rows | Row order vs `67:1015` | Full glass card clone |
| Connection `2075:235` | `ConnectionSuccessActivity` | 16sp `sans-serif-medium` headline; `connection_avatar_ring`; CTA in stroked `MaterialCardView` | Decorative vectors (rings use existing dashed drawable) |
| Suggested provider | `ProviderRecommendationActivity` | Provider card + stacked action cards | — |

### RPC / backend gaps (v1)

**None required** for this wave: strategy is **option (1)** — single `p_proposed_price` + optional breakdown in `message`. See [BACKEND_CONTRACT_V1.md](../supabase/BACKEND_CONTRACT_V1.md).

---

**Assets:** Vector “connection” illustration in `2075:235` is not bundled; apps keep lightweight dashed rings + Glide avatar.

## Admin portal

No new tables or RPCs in this wave. [connecther-admin-portal/README.md](../../connecther-admin-portal/README.md) links here and to Figma for operators.
