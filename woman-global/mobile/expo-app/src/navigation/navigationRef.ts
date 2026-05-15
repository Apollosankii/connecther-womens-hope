import { createNavigationContainerRef } from '@react-navigation/native';

import type { AppStackParamList, MainTabParamList } from '@/navigation/types';

export const navigationRef = createNavigationContainerRef<AppStackParamList>();

const HOME_TAB_BY_TYPE: Partial<Record<string, keyof MainTabParamList>> = {
  booking_created: 'Jobs',
  booking_declined: 'Jobs',
  booking_expired: 'Jobs',
  booking_cancelled: 'Jobs',
  provider_approved: 'Profile',
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

  if (type === 'message' && chatCode) {
    navigationRef.navigate('Chat', { chatCode });
    return;
  }

  const tab = HOME_TAB_BY_TYPE[type] ?? 'Home';
  navigationRef.navigate('MainTabs', { screen: tab });
}
