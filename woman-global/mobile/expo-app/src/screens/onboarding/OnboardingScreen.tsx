import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useCallback, useRef, useState } from 'react';
import {
  Dimensions,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import PagerView from 'react-native-pager-view';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppButton } from '@/components/ui/AppButton';
import { Colors } from '@/theme/colors';
import { Spacing } from '@/theme/spacing';
import { ONBOARDING_SLIDES, type OnboardingSlide } from '@/screens/onboarding/onboardingContent';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

const ICON_BY_SLIDE: Record<OnboardingSlide['icon'], keyof typeof MaterialCommunityIcons.glyphMap> = {
  welcome: 'human-greeting-variant',
  services: 'broom',
  how: 'calendar-check',
  ready: 'account-check',
};

export function OnboardingScreen({ onDone }: { onDone: () => void }) {
  const insets = useSafeAreaInsets();
  const pagerRef = useRef<PagerView>(null);
  const [page, setPage] = useState(0);
  const lastIndex = ONBOARDING_SLIDES.length - 1;

  const goNext = useCallback(() => {
    if (page >= lastIndex) {
      onDone();
      return;
    }
    pagerRef.current?.setPage(page + 1);
  }, [lastIndex, onDone, page]);

  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <View style={styles.header}>
        <Pressable onPress={onDone} hitSlop={12} accessibilityRole="button" accessibilityLabel="Skip onboarding">
          <Text style={styles.skip}>Skip</Text>
        </Pressable>
      </View>

      <PagerView
        ref={pagerRef}
        style={styles.pager}
        initialPage={0}
        onPageSelected={(e) => setPage(e.nativeEvent.position)}
      >
        {ONBOARDING_SLIDES.map((slide, index) => (
          <View key={index} style={styles.page} collapsable={false}>
            <SlideContent slide={slide} />
          </View>
        ))}
      </PagerView>

      <View style={styles.footer}>
        <View style={styles.dots}>
          {ONBOARDING_SLIDES.map((_, i) => (
            <View key={i} style={[styles.dot, i === page && styles.dotActive]} />
          ))}
        </View>
        <AppButton onPress={goNext}>{page >= lastIndex ? 'Get started' : 'Next'}</AppButton>
      </View>
    </View>
  );
}

function SlideContent({ slide }: { slide: OnboardingSlide }) {
  const iconName = ICON_BY_SLIDE[slide.icon];
  return (
    <View style={styles.slideInner}>
      <View style={styles.iconWrap}>
        <MaterialCommunityIcons name={iconName} size={72} color={Colors.primary} />
      </View>
      <Text style={styles.title}>{slide.title}</Text>
      <Text style={styles.body}>{slide.body}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  header: {
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
    alignItems: 'flex-end',
  },
  skip: {
    color: Colors.accent,
    fontSize: 16,
    fontWeight: '600',
  },
  pager: {
    flex: 1,
  },
  page: {
    width: SCREEN_WIDTH,
    flex: 1,
    justifyContent: 'center',
  },
  slideInner: {
    flex: 1,
    paddingHorizontal: Spacing.xl,
    justifyContent: 'center',
    gap: Spacing.md,
  },
  iconWrap: {
    alignSelf: 'center',
    marginBottom: Spacing.lg,
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: Colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: Colors.outline,
  },
  title: {
    fontSize: 26,
    fontWeight: '800',
    color: Colors.onBackground,
    textAlign: 'center',
  },
  body: {
    fontSize: 15,
    color: Colors.onSurfaceVariant,
    lineHeight: 22,
    textAlign: 'center',
  },
  footer: {
    paddingHorizontal: Spacing.lg,
    paddingBottom: Spacing.md,
    gap: Spacing.lg,
  },
  dots: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: Colors.outline,
  },
  dotActive: {
    backgroundColor: Colors.primary,
    width: 22,
  },
});
