import { MaterialCommunityIcons } from '@expo/vector-icons';
import * as ScreenCapture from 'expo-screen-capture';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Platform,
  ScrollView,
  StyleSheet,
  View,
  type NativeSyntheticEvent,
} from 'react-native';
import type { WebViewNavigation } from 'react-native-webview';
import { WebView } from 'react-native-webview';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import type { ThemeColors } from '@/theme/types';
import { buildDocumentViewerChain, isDirectImageUrl, isMozillaPdfJsViewerUrl } from '@/utils/secureDocumentViewer';

const VIEWER_TIMEOUT_MS = 26_000;
const CHROME_LIKE_UA =
  'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

const PDF_JS_TOOLBAR_LOCKDOWN = `
(function(){
  try {
    var css = '#download,#openFile,#secondaryOpenFile,.toolbarButton.download,.toolbarButton.openFile{display:none!important;}';
    var s = document.createElement('style');
    s.appendChild(document.createTextNode(css));
    (document.head || document.documentElement).appendChild(s);
  } catch (e) {}
  true;
})();
`;

type Props = NativeStackScreenProps<AppStackParamList, 'ProviderDocuments'>;

export function ProviderDocumentsScreen({ navigation, route }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const url = String(route.params?.url ?? '').trim();
  const title = route.params?.title?.trim();
  const canOpen = useMemo(() => /^https?:\/\//i.test(url), [url]);
  const isImage = useMemo(() => canOpen && isDirectImageUrl(url), [canOpen, url]);
  const chain = useMemo(() => (canOpen && !isImage ? buildDocumentViewerChain(url) : []), [canOpen, isImage, url]);
  const [chainIndex, setChainIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadTimedOut, setLoadTimedOut] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const chainIndexRef = useRef(0);
  const chainLenRef = useRef(0);
  const webRef = useRef<WebView>(null);

  const viewerUrl = chain[chainIndex] ?? '';

  useEffect(() => {
    chainIndexRef.current = chainIndex;
  }, [chainIndex]);
  useEffect(() => {
    chainLenRef.current = chain.length;
  }, [chain.length]);

  useEffect(() => {
    setChainIndex(0);
  }, [url]);

  useEffect(() => {
    setLoadTimedOut(false);
    setLoadError(null);
    setLoading(true);
  }, [url, chainIndex]);

  useEffect(() => {
    if (!viewerUrl) return undefined;
    const timer = setTimeout(() => {
      setLoading((stillLoading) => {
        if (!stillLoading) return false;
        const i = chainIndexRef.current;
        const len = chainLenRef.current;
        if (i < len - 1) {
          setChainIndex(i + 1);
          return true;
        }
        setLoadTimedOut(true);
        return false;
      });
    }, VIEWER_TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [viewerUrl]);

  const protect = useCallback(async () => {
    if (Platform.OS === 'web') return;
    try {
      await ScreenCapture.preventScreenCaptureAsync();
    } catch {
      /* optional native module */
    }
  }, []);

  const unprotect = useCallback(async () => {
    if (Platform.OS === 'web') return;
    try {
      await ScreenCapture.allowScreenCaptureAsync();
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    void protect();
    return () => {
      void unprotect();
    };
  }, [protect, unprotect]);

  const hidePdfJsChrome = useCallback(() => {
    if (!isMozillaPdfJsViewerUrl(viewerUrl)) return;
    webRef.current?.injectJavaScript(PDF_JS_TOOLBAR_LOCKDOWN);
  }, [viewerUrl]);

  const handleRetry = useCallback(() => {
    setChainIndex(0);
    setLoadTimedOut(false);
    setLoadError(null);
    setLoading(true);
  }, []);

  const onLoadProgress = useCallback(
    ({ nativeEvent }: NativeSyntheticEvent<{ progress: number }>) => {
      if (nativeEvent.progress >= 0.97) {
        setLoading(false);
        setLoadTimedOut(false);
      }
    },
    [],
  );

  const onNavigationStateChange = useCallback((navState: WebViewNavigation) => {
    if (navState.loading === false) {
      setLoading(false);
    }
  }, []);

  const onHttpError = useCallback(
    (e: NativeSyntheticEvent<{ statusCode: number; description?: string; url?: string }>) => {
      const code = e.nativeEvent.statusCode;
      if (code < 400) return;
      if (chainIndexRef.current < chainLenRef.current - 1) return;
      setLoading(false);
      setLoadError(e.nativeEvent.description ?? `Could not load document (HTTP ${code}).`);
    },
    [],
  );

  return (
    <Screen padded={false} style={styles.screenRoot}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <View style={{ flex: 1 }}>
          <AppText variant="h3" numberOfLines={1}>
            {title || 'Document'}
          </AppText>
          <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }} numberOfLines={2}>
            View only in the app · saving to your device is not supported
            {loadTimedOut ? ' · If preview failed, try Retry below.' : ''}
          </AppText>
        </View>
      </View>
      {!canOpen ? (
        <View style={styles.body}>
          <AppText variant="body" style={{ color: colors.onSurface }}>
            Missing or invalid document URL.
          </AppText>
        </View>
      ) : Platform.OS === 'web' ? (
        <View style={styles.body}>
          <AppText variant="body" style={{ color: colors.onSurface }}>
            Document preview is not available on web in this build. Use the mobile app.
          </AppText>
        </View>
      ) : isImage ? (
        <ScrollView style={styles.webWrap} contentContainerStyle={styles.imageScroll}>
          <Image source={{ uri: url }} style={styles.fullImage} resizeMode="contain" accessibilityIgnoresInvertColors />
        </ScrollView>
      ) : (
        <View style={styles.webWrap}>
          {loading ? (
            <View style={styles.loader}>
              <ActivityIndicator size="large" color={colors.primary} />
              <AppText variant="caption" style={[styles.loaderHint, { color: colors.onSurfaceVariant }]}>
                Loading preview…
              </AppText>
            </View>
          ) : null}
          {loadError ? (
            <View style={styles.errorBanner}>
              <AppText variant="body" style={{ color: colors.onSurface }}>
                {loadError}
              </AppText>
              <AppButton variant="outline" onPress={handleRetry} style={styles.retryBtn}>
                Retry
              </AppButton>
            </View>
          ) : null}
          {loadTimedOut && !loadError ? (
            <View style={styles.errorBanner}>
              <AppText variant="body" style={{ color: colors.onSurface }}>
                This preview is taking too long. You can retry from the start or wait — the document may still appear
                below.
              </AppText>
              <AppButton variant="outline" onPress={handleRetry} style={styles.retryBtn}>
                Retry
              </AppButton>
            </View>
          ) : null}
          <WebView
            ref={webRef}
            key={`${chainIndex}-${viewerUrl.slice(0, 120)}`}
            source={{ uri: viewerUrl }}
            style={styles.webview}
            userAgent={CHROME_LIKE_UA}
            onLoadProgress={onLoadProgress}
            onLoadEnd={() => {
              setLoading(false);
              hidePdfJsChrome();
            }}
            onError={() => {
              setLoading(false);
              const i = chainIndexRef.current;
              const len = chainLenRef.current;
              if (i < len - 1) {
                setChainIndex(i + 1);
                setLoading(true);
              } else {
                setLoadError('Could not load this document in the viewer.');
              }
            }}
            onHttpError={onHttpError}
            onNavigationStateChange={onNavigationStateChange}
            {...(Platform.OS === 'ios'
              ? {
                  onFileDownload: () => {
                    Alert.alert('View only', 'Provider documents cannot be downloaded from the app.');
                  },
                }
              : {})}
            javaScriptEnabled
            domStorageEnabled
            sharedCookiesEnabled
            thirdPartyCookiesEnabled
            setSupportMultipleWindows={false}
            originWhitelist={['https://', 'http://']}
            allowsInlineMediaPlayback
            mediaPlaybackRequiresUserAction
            {...(Platform.OS === 'android'
              ? {
                  mixedContentMode: 'compatibility' as const,
                  lackPermissionToDownloadMessage:
                    'View only — downloads are turned off for provider documents.',
                }
              : {})}
            cacheEnabled
            onShouldStartLoadWithRequest={(req) => {
              const u = req.url.toLowerCase();
              if (u.startsWith('file:') || u.startsWith('intent:')) {
                return false;
              }
              return true;
            }}
          />
        </View>
      )}
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    screenRoot: {
      flex: 1,
    },
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      backgroundColor: colors.background,
    },
    body: {
      flex: 1,
      paddingHorizontal: 16,
      paddingVertical: 12,
    },
    webWrap: {
      flex: 1,
      minHeight: 200,
      backgroundColor: colors.surface,
    },
    webview: {
      flex: 1,
      backgroundColor: colors.surface,
    },
    loader: {
      ...StyleSheet.absoluteFillObject,
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 2,
      backgroundColor: colors.surface,
      paddingHorizontal: 24,
    },
    loaderHint: {
      marginTop: 12,
      textAlign: 'center',
    },
    errorBanner: {
      position: 'absolute',
      left: 12,
      right: 12,
      top: 12,
      zIndex: 3,
      padding: 12,
      borderRadius: 12,
      backgroundColor: colors.surface,
      borderWidth: StyleSheet.hairlineWidth,
      borderColor: colors.outlineSoft,
      gap: 10,
    },
    retryBtn: {
      alignSelf: 'stretch',
    },
    imageScroll: {
      flexGrow: 1,
      justifyContent: 'center',
      padding: 12,
    },
    fullImage: {
      width: '100%',
      minHeight: 400,
      aspectRatio: 0.75,
    },
  });
}
