import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Platform, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { MainTabParamList } from '@/navigation/types';
import { HomeScreen } from '@/screens/main/HomeScreen';
import { JobsScreen } from '@/screens/main/JobsScreen';
import { MessagesScreen } from '@/screens/main/MessagesScreen';
import { ProfileScreen } from '@/screens/main/ProfileScreen';
import { ServicesScreen } from '@/screens/main/ServicesScreen';
import { useTheme } from '@/providers/ThemeProvider';
import {
  FLOATING_TAB_BAR_BOTTOM_GAP,
  getFloatingTabBarNativeHeight,
} from '@/navigation/floatingTabBar';

const Tab = createBottomTabNavigator<MainTabParamList>();

export function MainTabs() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  const TAB_BAR_INSET_X = 16;
  const TAB_BAR_RADIUS = 18;

  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.navItem,
        tabBarHideOnKeyboard: true,
        // Floating pill tab bar.
        tabBarStyle: {
          position: 'absolute',
          left: TAB_BAR_INSET_X,
          right: TAB_BAR_INSET_X,
          bottom: Math.max(insets.bottom, FLOATING_TAB_BAR_BOTTOM_GAP),
          backgroundColor: colors.surface,
          borderTopWidth: 1,
          borderTopColor: colors.outlineSoft,
          height: getFloatingTabBarNativeHeight(insets.bottom),
          paddingBottom: Math.max(insets.bottom - 6, 0),
          paddingTop: 8,
          borderRadius: TAB_BAR_RADIUS,
          ...(Platform.OS === 'android'
            ? {
                elevation: 10,
              }
            : {
                shadowColor: '#000',
                shadowOffset: { width: 0, height: 8 },
                shadowOpacity: 0.08,
                shadowRadius: 18,
              }),
        },
        tabBarItemStyle: {
          paddingTop: 2,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
          marginTop: -2,
        },
      }}
    >
      <Tab.Screen
        name="Home"
        component={HomeScreen}
        options={{
          tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="home-variant" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="Services"
        component={ServicesScreen}
        options={{
          tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="view-grid" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="Messages"
        component={MessagesScreen}
        options={{
          tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="message-text-outline" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="Jobs"
        component={JobsScreen}
        options={{
          tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="briefcase-outline" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="Profile"
        component={ProfileScreen}
        options={{
          tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="account-circle-outline" color={color} size={size} />,
        }}
      />
    </Tab.Navigator>
  );
}

