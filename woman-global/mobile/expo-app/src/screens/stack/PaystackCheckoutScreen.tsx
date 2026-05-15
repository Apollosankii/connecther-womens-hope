import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import * as WebBrowser from 'expo-web-browser';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Platform, Pressable, StyleSheet, View } from 'react-native';
import type { WebViewNavigation } from 'react-native-webview';
import { WebView } from 'react-native-webview';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { verifyPaystackTransaction, waitForSubscriptionActive } from '@/services/payments/paystackService';
import type { ThemeColors } from '@/theme/types';

type Props = NativeStackScreenProps<AppStackParamList, 'PaystackCheckout'>;

function isPaystackSuccessUrl(url: string): boolean {
  const u = url.toLowerCase();
  return u.includes('success') || u.includes('callback') || u.includes('reference=');
}

export function PaystackCheckoutScreen({ navigation, route }: Props) {
  const { planId, planName, priceLabel, authorizationUrl, reference } = route.params;
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const webRef = useRef<WebView>(null);
  const [verifying, setVerifying] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const finishSuccess = useCallback(() => {
    Alert.alert('Subscription active', 'Your plan is now active. You can continue booking providers.', [
      { text: 'OK', onPress: () => navigation.popToTop() },
    ]);
  }, [navigation]);

  const runVerify = useCallback(async () => {
    setVerifying(true);
    try {
      const verified = await verifyPaystackTransaction(reference);
      if (verified) {
        finishSuccess();
        return;
      }
      const polled = await waitForSubscriptionActive(planId, reference);
      if (polled) {
        finishSuccess();
        return;
      }
      Alert.alert(
        'Payment pending',
        'We could not confirm your payment yet. If you completed checkout, wait a moment and tap Verify payment again.',
      );
    } catch (e) {
      Alert.alert('Verify failed', e instanceof Error ? e.message : 'Could not verify payment.');
    } finally {
      setVerifying(false);
    }
  }, [reference, planId, finishSuccess]);

  const openInBrowser = useCallback(async () => {
    try {
      const url = authorizationUrl;
      if (!url) {
        Alert.alert('Checkout', 'No checkout URL available.');
        return;
      }
      await WebBrowser.openBrowserAsync(url);
      Alert.alert('Complete payment', 'When you finish paying in the browser, return here and tap Verify payment.', [
        { text: 'Verify payment', onPress: () => void runVerify() },
        { text: 'Cancel', style: 'cancel' },
      ]);
    } catch (e) {
      Alert.alert('Checkout', e instanceof Error ? e.message : 'Could not open browser checkout.');
    }
  }, [authorizationUrl, runVerify]);

  const onNavigationStateChange = useCallback(
    (nav: WebViewNavigation) => {
      if (nav.url && isPaystackSuccessUrl(nav.url)) {
        void runVerify();
      }
    },
    [runVerify],
  );

  if (Platform.OS === 'ios') {
    return (
      <Screen padded={false}>
        <View style={styles.centered}>
          <AppText variant="body" style={{ textAlign: 'center', paddingHorizontal: 24 }}>
            Paystack checkout is not available on iOS. Use App Store subscriptions from the Subscriptions screen.
          </AppText>
          <AppButton variant="outline" onPress={() => navigation.goBack()} style={{ marginTop: 12 }}>
            Go back
          </AppButton>
        </View>
      </Screen>
    );
  }

  const checkoutUrl = authorizationUrl?.trim();
  if (!checkoutUrl) {
    return (
      <Screen padded={false}>
        <View style={styles.header}>
          <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
            <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
          </IconButton>
          <AppText variant="h3">Checkout</AppText>
        </View>
        <View style={styles.centered}>
          <AppText variant="body">Checkout URL is missing.</AppText>
          <AppButton variant="outline" onPress={() => navigation.goBack()} style={{ marginTop: 12 }}>
            Go back
          </AppButton>
        </View>
      </Screen>
    );
  }

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <View style={{ flex: 1, minWidth: 0 }}>
          <AppText variant="h3" numberOfLines={1}>
            {planName}
          </AppText>
          <AppText variant="caption" style={{ color: colors.onSurfaceVariant }}>
            {priceLabel}
          </AppText>
        </View>
      </View>

      {loadError ? (
        <View style={styles.centered}>
          <AppText variant="body" style={{ textAlign: 'center', paddingHorizontal: 24 }}>
            {loadError}
          </AppText>
          <AppButton variant="outline" onPress={() => void openInBrowser()} style={{ marginTop: 12 }}>
            Open in browser
          </AppButton>
        </View>
      ) : (
        <WebView
          ref={webRef}
          source={{ uri: checkoutUrl }}
          style={styles.web}
          onNavigationStateChange={onNavigationStateChange}
          onError={() => setLoadError('Could not load Paystack checkout in the app.')}
          onHttpError={() => setLoadError('Checkout page failed to load.')}
          startInLoadingState
          renderLoading={() => (
            <View style={styles.webLoading}>
              <ActivityIndicator size="large" color={colors.primary} />
            </View>
          )}
        />
      )}

      <View style={[styles.footer, { borderTopColor: colors.outlineSoft, backgroundColor: colors.surface }]}>
        <AppButton onPress={() => void runVerify()} loading={verifying} disabled={verifying}>
          I&apos;ve paid — verify
        </AppButton>
        <Pressable onPress={() => void openInBrowser()} style={styles.browserLink} accessibilityRole="button">
          <AppText variant="link">Open checkout in browser</AppText>
        </Pressable>
      </View>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      backgroundColor: colors.background,
    },
    web: {
      flex: 1,
    },
    webLoading: {
      ...StyleSheet.absoluteFillObject,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: colors.background,
    },
    footer: {
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 16,
      borderTopWidth: StyleSheet.hairlineWidth,
      gap: 8,
    },
    browserLink: {
      alignSelf: 'center',
      paddingVertical: 8,
    },
    centered: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
    },
  });
}
