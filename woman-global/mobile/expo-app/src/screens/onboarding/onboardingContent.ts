/**
 * Copy aligned with Android `OnboardingActivity.kt` + `strings.xml` `onboarding_page_desc`.
 * Drawables are not bundled here; icons approximate `onboarding_*` drawables.
 */
export type OnboardingSlide = {
  title: string;
  body: string;
  icon: 'welcome' | 'services' | 'how' | 'ready';
};

export const ONBOARDING_SLIDES: OnboardingSlide[] = [
  {
    icon: 'welcome',
    title: 'Welcome to ConnectHer',
    body:
      'Tired of spending hours searching for reliable cleaning and caregiver services? ConnectHer makes it easier than ever to book professionals house cleaners and caregivers at your convenience. Whether you need a one-time deep clean, regular house maintenance, or care giving services our app connects you with trusted, background-checked professionals in your area',
  },
  {
    icon: 'services',
    title: 'Trusted services',
    body:
      'Find house cleaners, caregivers, and more — from verified professionals near you.',
  },
  {
    icon: 'how',
    title: 'How it works',
    body:
      'Browse services, choose a provider, and book at your convenience — safely and quickly.',
  },
  {
    icon: 'ready',
    title: 'Ready?',
    body: 'Create an account or sign in to continue.',
  },
];
