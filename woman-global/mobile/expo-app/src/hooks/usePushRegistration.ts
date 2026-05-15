import * as Notifications from 'expo-notifications';
import { useEffect } from 'react';

import {
  clearPushRegistrationCache,
  configurePushNotifications,
  handleNotificationResponse,
  registerPushTokenWithBackend,
  subscribeAppForegroundPushRefresh,
  subscribePushTokenRefresh,
} from '@/services/push/pushNotifications';
/** Registers FCM with Supabase when signed in; handles notification taps. */
export function usePushRegistration(isLoggedIn: boolean) {
  useEffect(() => {
    configurePushNotifications();
  }, []);

  useEffect(() => {
    if (!isLoggedIn) {
      clearPushRegistrationCache();
      return;
    }
    const t = setTimeout(() => {
      void registerPushTokenWithBackend(true);
    }, 800);
    const unsubToken = subscribePushTokenRefresh(() => isLoggedIn);
    const unsubFg = subscribeAppForegroundPushRefresh(() => isLoggedIn);
    return () => {
      clearTimeout(t);
      unsubToken();
      unsubFg();
    };
  }, [isLoggedIn]);
  useEffect(() => {
    const responseSub = Notifications.addNotificationResponseReceivedListener((response) => {
      handleNotificationResponse(response);
    });

    void Notifications.getLastNotificationResponseAsync().then((response) => {
      if (response) handleNotificationResponse(response);
    });

    return () => responseSub.remove();
  }, []);
}
