import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react';
import { useColorScheme } from 'react-native';

import { DarkColors, LightColors } from '@/theme/palette';
import type { ThemeColors, ThemeMode } from '@/theme/types';

type ThemeCtx = {
  mode: ThemeMode;
  effectiveMode: 'light' | 'dark';
  colors: ThemeColors;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
};

const KEY = 'theme_mode_v1';

const Ctx = createContext<ThemeCtx | null>(null);

export function ThemeProvider({ children }: PropsWithChildren) {
  const system = useColorScheme() === 'dark' ? 'dark' : 'light';
  const [mode, setModeState] = useState<ThemeMode>('system');

  useEffect(() => {
    AsyncStorage.getItem(KEY)
      .then((v) => {
        if (v === 'light' || v === 'dark' || v === 'system') setModeState(v);
      })
      .catch(() => null);
  }, []);

  const effectiveMode = mode === 'system' ? system : mode;
  const colors = effectiveMode === 'dark' ? DarkColors : LightColors;

  const api = useMemo<ThemeCtx>(() => {
    const setMode = (m: ThemeMode) => {
      setModeState(m);
      AsyncStorage.setItem(KEY, m).catch(() => null);
    };
    const toggle = () => {
      setMode(effectiveMode === 'dark' ? 'light' : 'dark');
    };
    return { mode, effectiveMode, colors, setMode, toggle };
  }, [effectiveMode, mode, colors]);

  return <Ctx.Provider value={api}>{children}</Ctx.Provider>;
}

export function useTheme() {
  const v = useContext(Ctx);
  if (!v) throw new Error('ThemeProvider missing');
  return v;
}

