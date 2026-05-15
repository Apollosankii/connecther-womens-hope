import { useEffect } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** Diameter of the ring (px). */
  size: number;
  /** Solid fill color of the ring (use semi-transparent color). */
  color: string;
  /** Duration for one wave cycle. Matches Android default: 1700ms. */
  durationMs?: number;
  /** End scale. Matches Android default: 1.52. */
  scaleEnd?: number;
  /** Starting alpha multiplier. Android uses 0.58. */
  alphaStart?: number;
  /** Optional delay to stagger waves (e.g., duration/2). */
  delayMs?: number;
  style?: StyleProp<ViewStyle>;
};

/**
 * Expanding “shockwave” ring: scales up while fading out, looping forever.
 * This mirrors Android `SosShockwaveAnimator` parameters (duration 1700ms, scaleEnd 1.52, alphaStart 0.58).
 */
export function ShockwaveRing({
  size,
  color,
  durationMs = 1700,
  scaleEnd = 1.52,
  alphaStart = 0.58,
  delayMs = 0,
  style,
}: Props) {
  const p = useSharedValue(0);

  useEffect(() => {
    // Match Android: DecelerateInterpolator(1.15f) ≈ easing-out curve.
    const easing = Easing.out(Easing.cubic);
    const wave = withTiming(1, { duration: durationMs, easing });
    p.value = withDelay(
      Math.max(0, delayMs),
      withRepeat(wave, -1, false, () => {
        // reset
        p.value = 0;
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [delayMs, durationMs]);

  const aStyle = useAnimatedStyle(() => {
    const scale = interpolate(p.value, [0, 1], [1, scaleEnd]);
    const opacity = interpolate(p.value, [0, 1], [alphaStart, 0]);
    return {
      transform: [{ scale }],
      opacity,
    };
  });

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        styles.ring,
        { width: size, height: size, borderRadius: size / 2, backgroundColor: color },
        aStyle,
        style,
      ]}
    />
  );
}

export function ShockwaveRings({
  outerSize,
  middleSize,
  outerColor,
  middleColor,
  style,
}: {
  outerSize: number;
  middleSize: number;
  outerColor: string;
  middleColor: string;
  style?: StyleProp<ViewStyle>;
}) {
  const middleOffset = (outerSize - middleSize) / 2;
  return (
    <View pointerEvents="none" style={[styles.container, { width: outerSize, height: outerSize }, style]}>
      {/* Outer ring is anchored at (0,0) inside the fixed-size wrapper */}
      <ShockwaveRing size={outerSize} color={outerColor} style={styles.abs0} />
      {/* Middle ring is perfectly centered inside the outer ring */}
      <ShockwaveRing
        size={middleSize}
        color={middleColor}
        delayMs={850}
        style={[styles.abs0, { left: middleOffset, top: middleOffset }]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    position: 'absolute',
  },
  ring: {
    position: 'absolute',
  },
  abs0: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
});

