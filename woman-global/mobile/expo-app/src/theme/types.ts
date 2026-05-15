export type ThemeColors = {
  background: string;
  /** Soft rose canvas for booking-style stacks (matches Figma #F5EEEC in light). */
  softCanvas: string;
  surface: string;
  surfaceVariant: string;
  onBackground: string;
  onSurface: string;
  onSurfaceVariant: string;
  outline: string;
  outlineSoft: string;
  primary: string;
  primaryVariant: string;
  onPrimary: string;
  accent: string;
  navItem: string;
  mutedIcon: string;
  scrim: string;
  chat: {
    headerSurface: string;
    threadBackground: string;
    composerSurface: string;
    divider: string;
    bubbleOutgoing: string;
    bubbleIncoming: string;
    textOnOutgoing: string;
    textOnIncoming: string;
    textSubtitle: string;
    inputFill: string;
    iconMuted: string;
    hireOutline: string;
  };
  bookingStatus: {
    pendingText: string;
    acceptedText: string;
    declinedText: string;
    cancelledText: string;
  };
  profile: { verified: string };
  sos: { pulse_outer: string; pulse_middle: string };
};

export type ThemeMode = 'system' | 'light' | 'dark';

