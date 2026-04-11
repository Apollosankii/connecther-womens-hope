import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useEffect, useState } from 'react';

const KEY = 'isFirstLaunch';

export function useFirstLaunch() {
  const [loading, setLoading] = useState(true);
  const [isFirstLaunch, setIsFirstLaunch] = useState<boolean>(false);

  useEffect(() => {
    (async () => {
      const raw = await AsyncStorage.getItem(KEY);
      // Android uses boolean default true; mirror that behavior.
      const val = raw == null ? true : raw === 'true';
      setIsFirstLaunch(val);
      setLoading(false);
    })();
  }, []);

  const completeOnboarding = useCallback(async () => {
    await AsyncStorage.setItem(KEY, 'false');
    setIsFirstLaunch(false);
  }, []);

  return { loading, isFirstLaunch, completeOnboarding };
}

