export type Service = {
  id: number;
  name: string;
  service_pic?: string | null;
  description?: string | null;
  min_price?: number | null;
  search_radius_meters?: number | null;
  require_location_detail?: boolean | null;
  location_detail_schema?: unknown | null;
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
};

export type ActiveSubscription = {
  id: number;
  plan_id: number;
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
  is_service_provider?: boolean | null;
  is_provider_application_pending?: boolean | null;
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
};

export type Conversation = {
  chat_code: string;
  other_user_name?: string | null;
  last_message?: string | null;
  updated_at?: string | null;
};

export type ChatMessage = {
  id: number;
  chat_code: string;
  content: string;
  created_at?: string | null;
  sender_user_id?: number | null;
};

