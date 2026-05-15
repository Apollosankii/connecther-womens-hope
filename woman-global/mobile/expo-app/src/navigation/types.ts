import type { NavigatorScreenParams } from '@react-navigation/native';

export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
  Terms: undefined;
};

export type MainTabParamList = {
  Home: undefined;
  Services: undefined;
  Messages: undefined;
  Jobs: undefined;
  Profile: undefined;
};

export type AppStackParamList = {
  MainTabs: NavigatorScreenParams<MainTabParamList> | undefined;
  AllServices: undefined;
  FullServices: undefined;
  Search: undefined;
  CategoryUsers: { serviceId: number; serviceName?: string; prefillTotal?: number; quoteLinesJson?: string };
  /** One-at-a-time match — Kotlin `ProviderRecommendationActivity` + `get_providers_for_service_near`. */
  ProviderRecommendation: {
    serviceId: number;
    serviceName?: string;
    prefillTotal?: number;
    quoteLinesJson?: string;
  };
  /** Task menu (quantities / toggles) — parity with Android `ServiceMenuActivity` + `ServiceTaskMenuParser`. */
  ServiceMenu: {
    serviceId: number;
    serviceName?: string;
    providerRef?: string;
    providerId?: number;
    providerDisplayName?: string;
    providerPic?: string | null;
  };
  BookJob: undefined;
  ProviderProfile: {
    providerId: number;
    providerRef: string;
    serviceId: number;
    serviceName?: string;
    providerDisplayName?: string;
    providerPic?: string | null;
    providerTitle?: string | null;
    providerOccupation?: string | null;
    providerWorkingHours?: string | null;
    providerAreaLabel?: string | null;
    /** From `ServiceMenu` → browse flow; skip second menu when booking. */
    prefillTotal?: number;
    quoteLinesJson?: string;
  };
  RequestBooking: {
    providerId: number;
    providerRef: string;
    serviceId: number;
    serviceName?: string;
    /** Shown on post-booking connection screen (optional). */
    providerDisplayName?: string;
    providerPic?: string | null;
    /** From task menu / quote intent (Android `EXTRA_PREFILL_PRICE` / `EXTRA_QUOTE_LINES_JSON`). */
    prefillPrice?: number;
    quoteLinesJson?: string;
  };
  ConnectionSuccess: {
    providerId: number;
    providerRef: string;
    serviceId: number;
    serviceName?: string;
    providerDisplayName?: string;
    providerPic?: string | null;
    /** After booking request was sent — hide “Continue to booking” and use post-submit copy. */
    postBooking?: boolean;
    prefillPrice?: number;
    quoteLinesJson?: string;
  };
  JobRating: {
    jobId: number;
    rateeName: string;
    rateeRole: string;
    serviceName?: string;
    rateePic?: string | null;
  };
  Conversations: undefined;
  Chat: { chatCode: string; title?: string; peerPic?: string | null };
  Settings: undefined;
  PasswordChange: undefined;
  AboutUs: undefined;
  Notifications: undefined;
  ReportProblem: undefined;
  Terms: undefined;
  Panic: undefined;
  EmergencyContacts: undefined;
  ProviderApplication: undefined;
  ProviderDocuments: { url: string; title?: string };
  ManageProviderDocuments: undefined;
  /** Provider settings: online/offline, headline, hours (Kotlin `ProviderProfileActivity`). */
  ManageProviderProfile: undefined;
  ProviderBookingRequests: undefined;
  /** Kotlin `SubscriptionsActivity` — opened from booking connect-limit errors. */
  Subscriptions: undefined;
  PaystackCheckout: {
    planId: number;
    planName: string;
    priceLabel: string;
    authorizationUrl: string;
    reference: string;
    email: string;
  };
};

