import { createNavigationContainerRef } from '@react-navigation/native';

import type { AppStackParamList, MainTabParamList } from '@/navigation/types';

export const navigationRef = createNavigationContainerRef<AppStackParamList>();

const HOME_TAB_BY_TYPE: Partial<Record<string, keyof MainTabParamList>> = {
  booking_created: 'Jobs',
  booking_accepted: 'Jobs',
  booking_declined: 'Jobs',
  booking_expired: 'Jobs',
  booking_cancelled: 'Jobs',
  job_completed: 'Jobs',
  provider_approved: 'Profile',
  provider_suspended: 'Profile',
  provider_unsuspended: 'Profile',
  services: 'Services',
  messages: 'Messages',
  jobs: 'Jobs',
  profile: 'Profile',
  home: 'Home',
  general: 'Home',
};

/** Kotlin `NotificationService.buildTapIntent` parity. */
export function navigateFromPushPayload(payload: Record<string, unknown>) {
  if (!navigationRef.isReady()) return;

  const type = String(payload.type ?? '').trim();
  const chatCode = String(payload.chat_code ?? '').trim();
  const jobIdRaw = String(payload.job_id ?? '').trim();
  const jobId = jobIdRaw ? Number(jobIdRaw) : NaN;

  if (type === 'message' && chatCode) {
    navigationRef.navigate('Chat', { chatCode });
    return;
  }

  if ((type === 'booking_accepted' || type === 'job_completed') && chatCode) {
    navigationRef.navigate('Chat', { chatCode });
    return;
  }

  if (type === 'job_completed' && Number.isFinite(jobId) && jobId > 0) {
    navigationRef.navigate('ActiveJob', { jobId });
    return;
  }

  if (
    (type === 'booking_created' ||
      type === 'booking_declined' ||
      type === 'booking_expired' ||
      type === 'booking_cancelled') &&
    navigationRef.isReady()
  ) {
    navigationRef.navigate('ProviderBookingRequests');
    return;
  }

  const tab = HOME_TAB_BY_TYPE[type] ?? 'Home';
  navigationRef.navigate('MainTabs', { screen: tab });
}
