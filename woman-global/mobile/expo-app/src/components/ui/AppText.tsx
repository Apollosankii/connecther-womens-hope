import type { PropsWithChildren } from 'react';
import { StyleSheet, Text, type StyleProp, type TextProps, type TextStyle } from 'react-native';

import { useTheme } from '@/providers/ThemeProvider';
import { Typography } from '@/theme/typography';

type Variant =
  | 'appTitle'
  | 'h1'
  | 'h2'
  | 'h3'
  | 'sectionTitle'
  | 'rowTitle'
  | 'body'
  | 'bodyStrong'
  | 'caption'
  | 'link'
  | 'overline';

type Props = PropsWithChildren<
  TextProps & {
    variant?: Variant;
    style?: StyleProp<TextStyle>;
  }
>;

export function AppText({ variant = 'body', style, children, ...rest }: Props) {
  const { colors } = useTheme();
  const base = variantStyles[variant] ?? variantStyles.body;
  const color =
    variant === 'appTitle' || variant === 'h1' || variant === 'h2' || variant === 'h3' || variant === 'sectionTitle'
      ? colors.onBackground
      : variant === 'rowTitle' || variant === 'bodyStrong'
        ? colors.onSurface
        : variant === 'link' || variant === 'overline'
          ? colors.accent
          : colors.onSurfaceVariant;
  return (
    <Text {...rest} style={[base, { color }, style]}>
      {children}
    </Text>
  );
}

const variantStyles = StyleSheet.create({
  appTitle: Typography.appTitle,
  h1: Typography.h1,
  h2: Typography.h2,
  h3: Typography.h3,
  sectionTitle: Typography.sectionTitle,
  rowTitle: Typography.rowTitle,
  body: Typography.body,
  bodyStrong: Typography.bodyStrong,
  caption: Typography.caption,
  link: Typography.link,
  overline: Typography.overline,
});

