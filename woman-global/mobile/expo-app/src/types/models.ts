export type Service = {
  id: number;
  name: string;
  service_pic?: string | null;
  /** Legacy / mirror of `service_pic` from some RPCs. */
  pic?: string | null;
  description?: string | null;
  min_price?: number | null;
  search_radius_meters?: number | null;
  require_location_detail?: boolean | null;
  location_detail_schema?: unknown | null;
  /** `services.task_menu` jsonb (admin task menu). */
  task_menu?: unknown | null;
  task_menu_json?: unknown | null;
};

export type SubscriptionPlan = {
  id: number;
  name: string;
  description?: string | null;
  price: number;
  currency?: string | null;
  duration_type?: 'month' | 'year' | string | null;
  duration_value?: number | null;
  features?: string[] | null;
  is_popular?: boolean | null;
  is_active?: boolean | null;
  sort_order?: number | null;
  apple_product_id?: string | null;
  revenuecat_entitlement_id?: string | null;
};

export type ActiveSubscription = {
  planId: number;
  planName: string;
  expiresAt: string | null;
  connectsGranted?: number | null;
  connectsUsed?: number | null;

  /** Legacy / raw columns (kept optional to avoid breaking old callers). */
  id?: number;
  plan_id?: number;
  status?: string | null;
  start_date?: string | null;
  end_date?: string | null;
};

export type UserProfile = {
  id: number;
  clerk_user_id: string;
  email?: string | null;
  first_name?: string | null;
  last_name?: string | null;
  phone?: string | null;
  pic?: string | null;
  prof_pic?: string | null;
  title?: string | null;
  occupation?: string | null;
  working_hours?: string | null;
  /** When false, provider is offline (hidden from suggestions, cannot accept bookings). */
  available_for_booking?: boolean | null;
  area_name?: string | null;
  country?: string | null;
  county?: string | null;
  /** Postgres `users.service_provider` — true when user is an approved provider (matches Kotlin `service_provider`). */
  service_provider?: boolean | null;
  /** Postgres `users.provider_application_pending` — application submitted, awaiting admin. */
  provider_application_pending?: boolean | null;
};

export type Job = {
  job_id: number;
  service?: string | null;
  price?: number | null;
  location?: string | null;
  status?: string | null;
  started_at?: string | null;
  completed_at?: string | null;
  i_am_client?: boolean | null;
  client?: string | null;
  provider?: string | null;
  my_review_submitted?: boolean | null;
};

export type BookingRequest = {
  id: number;
  service_id: number;
  status: 'pending' | 'accepted' | 'declined' | 'expired' | 'cancelled' | string;
  proposed_price?: number | null;
  location_text?: string | null;
  message?: string | null;
  expires_at?: string | null;
  created_at?: string | null;
  // display strings resolved by RPC
  provider_display?: string | null;
  client_display?: string | null;
  /** Derived from RPC `role` (`client` | `provider`) when present. */
  i_am_client?: boolean | null;
  role?: string | null;
  maps_url?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  location_extra?: unknown | null;
};

export type Conversation = {
  chat_code: string;
  other_user_name?: string | null;
  other_user_pic?: string | null;
  last_message?: string | null;
  updated_at?: string | null;
};

export type Provider = {
  id: number;
  user_name?: string | null;
  first_name?: string | null;
  last_name?: string | null;
  title?: string | null;
  phone?: string | null;
  pic?: string | null;
  area_name?: string | null;
  country?: string | null;
  county?: string | null;
  occupation?: string | null;
  working_hours?: string | null;
  wh_badge?: string | null;
  /** From `get_providers_for_service_near` + live_location (client-side sort). */
  latitude?: number | null;
  longitude?: number | null;
};

export type ChatMessage = {
  id: number;
  chat_code: string;
  content: string;
  created_at?: string | null;
  /** Supabase `messages.sender_id` (string uid from `get_my_user_id`). */
  sender_id?: string | null;
};

