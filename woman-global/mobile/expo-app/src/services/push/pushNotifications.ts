import * as Application from 'expo-application';
import * as Notifications from 'expo-notifications';
import { AppState, Platform, type AppStateStatus } from 'react-native';

import { navigateFromPushPayload } from '@/navigation/navigationRef';
import { upsertMyDevice } from '@/services/api/devices';
/** Matches Kotlin `default_notification_channel_id` / Firebase manifest meta-data. */
export const PUSH_CHANNEL_ID = 'FCM_CHANNEL_ID';

let lastRegisteredToken: string | null = null;
let configured = false;

export function configurePushNotifications() {
  if (configured || Platform.OS === 'web') return;
  configured = true;

  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowAlert: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
      shouldShowBanner: true,
      shouldShowList: true,
    }),
  });
}

async function ensureAndroidNotificationChannel() {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(PUSH_CHANNEL_ID, {
    name: 'ConnectHer',
    importance: Notifications.AndroidImportance.HIGH,
    sound: 'default',
    vibrationPattern: [0, 250, 250, 250],
  });
}

async function getStableDeviceId(): Promise<string> {
  if (Platform.OS === 'android') {
    return Application.getAndroidId() ?? 'default';
  }
  if (Platform.OS === 'ios') {
    const id = await Application.getIosIdForVendorAsync();
    return id ?? 'default';
  }
  return 'default';
}

/** Register native FCM token with Supabase (`upsert_my_device`). Kotlin: `PushRegistration.register`. */
export async function registerPushTokenWithBackend(force = false): Promise<boolean> {
  if (Platform.OS === 'web') return false;

  const { status: existing } = await Notifications.getPermissionsAsync();
  let finalStatus = existing;
  if (existing !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }
  if (finalStatus !== 'granted') {
    if (__DEV__) console.warn('[push] notification permission not granted');
    return false;
  }

  await ensureAndroidNotificationChannel();

  let tokenResult: Notifications.DevicePushToken;
  try {
    tokenResult = await Notifications.getDevicePushTokenAsync();
  } catch (e) {
    if (__DEV__) console.warn('[push] getDevicePushTokenAsync failed', e);
    return false;
  }

  if (Platform.OS === 'ios') {
    if (__DEV__) console.warn('[push] skipping iOS token — backend is FCM-only');
    return false;
  }

  const token =
    typeof tokenResult.data === 'string' ? tokenResult.data : String(tokenResult.data ?? '');
  if (!token) return false;
  if (!force && token === lastRegisteredToken) return true;

  const deviceId = await getStableDeviceId();
  const result = await upsertMyDevice(token, deviceId);
  if (result.ok) {
    lastRegisteredToken = token;
    if (__DEV__) console.log('[push] FCM token registered for device', deviceId.slice(0, 8));
    return true;
  }
  if (__DEV__) console.warn('[push] upsert_my_device failed:', result.error);
  return false;
}

export function subscribeAppForegroundPushRefresh(isActive: () => boolean): () => void {
  const onChange = (state: AppStateStatus) => {
    if (state === 'active' && isActive()) void registerPushTokenWithBackend();
  };
  const sub = AppState.addEventListener('change', onChange);
  return () => sub.remove();
}
export function clearPushRegistrationCache() {
  lastRegisteredToken = null;
}

export function payloadFromNotificationResponse(
  response: Notifications.NotificationResponse,
): Record<string, unknown> {
  const content = response.notification.request.content;
  const data = (content.data ?? {}) as Record<string, unknown>;
  if (Object.keys(data).length > 0) return data;
  return {
    title: content.title ?? undefined,
    body: content.body ?? undefined,
  };
}

export function handleNotificationResponse(response: Notifications.NotificationResponse) {
  navigateFromPushPayload(payloadFromNotificationResponse(response));
}

export function subscribePushTokenRefresh(isActive: () => boolean): () => void {
  const sub = Notifications.addPushTokenListener(() => {
    if (isActive()) void registerPushTokenWithBackend();
  });
  return () => sub.remove();
}
