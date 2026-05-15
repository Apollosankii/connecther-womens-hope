import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { StyleSheet, View } from 'react-native';

import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { Spinner } from '@/components/ui/Spinner';
import { useTheme } from '@/providers/ThemeProvider';
import { getPublicRatingStatsForUser, listPublicReviewsForUser, type PublicReviewItem } from '@/services/api/reviews';
import type { ThemeColors } from '@/theme/types';

function formatReviewDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function StarRow({ stars, size = 16 }: { stars: number; size?: number }) {
  const { colors } = useTheme();
  const filled = Math.max(0, Math.min(5, Math.round(stars)));
  return (
    <View style={{ flexDirection: 'row', gap: 2 }}>
      {Array.from({ length: 5 }, (_, i) => (
        <MaterialCommunityIcons
          key={i}
          name={i < filled ? 'star' : 'star-outline'}
          size={size}
          color={i < filled ? colors.accent : colors.onSurfaceVariant}
        />
      ))}
    </View>
  );
}

function ReviewCard({ review }: { review: PublicReviewItem }) {
  const { colors } = useTheme();
  const reviewer = (review.reviewerFirstName ?? '').trim() || 'Client';
  const service = (review.serviceName ?? '').trim() || 'Service';
  const comment = (review.reviewText ?? '').trim();

  return (
    <AppCard padding="sm" style={{ borderRadius: 12 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
        <StarRow stars={review.stars} size={14} />
        <AppText variant="caption" style={{ color: colors.onSurfaceVariant }}>
          {formatReviewDate(review.createdAt)}
        </AppText>
      </View>
      <AppText variant="bodyStrong" style={{ marginTop: 8, color: colors.onSurface }}>
        {reviewer} · {service}
      </AppText>
      {comment ? (
        <AppText variant="body" style={{ marginTop: 8, color: colors.onSurface, lineHeight: 22 }}>
          {comment}
        </AppText>
      ) : (
        <AppText variant="caption" style={{ marginTop: 6, color: colors.onSurfaceVariant, fontStyle: 'italic' }}>
          No written comment.
        </AppText>
      )}
    </AppCard>
  );
}

type Props = {
  providerId: number;
  /** Section title override */
  title?: string;
};

export function ProviderReviewsSection({ providerId, title = 'Ratings & reviews' }: Props) {
  const { colors } = useTheme();
  const styles = makeStyles(colors);

  const statsQ = useQuery({
    queryKey: ['reviews', 'stats', providerId],
    queryFn: () => getPublicRatingStatsForUser(providerId),
    enabled: providerId > 0,
  });

  const reviewsQ = useQuery({
    queryKey: ['reviews', 'list', providerId],
    queryFn: () => listPublicReviewsForUser(providerId, 50),
    enabled: providerId > 0,
  });

  const stats = statsQ.data;
  const reviews = reviewsQ.data ?? [];
  const reviewCount = stats?.reviewCount ?? reviews.length;
  const avgStars =
    stats?.avgStars ?? (reviews.length > 0 ? reviews.reduce((s, r) => s + r.stars, 0) / reviews.length : 0);
  const hasReviews = reviews.length > 0 || reviewCount > 0;

  return (
    <AppCard padding="md">
      <AppText variant="caption" style={styles.overline}>
        {title.toUpperCase()}
      </AppText>
      <AppText variant="caption" style={{ color: colors.onSurfaceVariant, marginBottom: 10 }}>
        Public feedback from clients after completed jobs. This is what seekers see on your profile.
      </AppText>

      {statsQ.isLoading || reviewsQ.isLoading ? (
        <View style={styles.centered}>
          <Spinner />
        </View>
      ) : statsQ.isError || reviewsQ.isError ? (
        <View style={{ gap: 8 }}>
          <AppText variant="body" style={{ color: colors.onSurface }}>
            Could not load reviews.
          </AppText>
          <AppButton
            variant="outline"
            onPress={() => {
              void statsQ.refetch();
              void reviewsQ.refetch();
            }}
          >
            Retry
          </AppButton>
        </View>
      ) : hasReviews ? (
        <View style={{ gap: 12 }}>
          <View style={{ gap: 6 }}>
            <StarRow stars={avgStars} size={18} />
            <AppText variant="bodyStrong" style={{ color: colors.onSurface }}>
              {avgStars.toFixed(1)} average · {reviewCount} public review{reviewCount === 1 ? '' : 's'}
            </AppText>
          </View>
          {reviews.length > 0 ? (
            reviews.map((r, idx) => (
              <ReviewCard key={`review-${providerId}-${idx}-${r.createdAt}-${r.stars}`} review={r} />
            ))
          ) : (
            <AppText variant="body" style={{ color: colors.onSurfaceVariant }}>
              Summary loaded but individual reviews are unavailable. Tap Retry.
            </AppText>
          )}
        </View>
      ) : (
        <AppText variant="body" style={{ color: colors.onSurface }}>
          No public reviews yet. After clients complete jobs and choose to publish feedback, reviews appear here.
        </AppText>
      )}
    </AppCard>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    overline: {
      fontSize: 11,
      fontWeight: '700',
      letterSpacing: 1.1,
      color: colors.primary,
      marginBottom: 8,
    },
    centered: {
      paddingVertical: 16,
      alignItems: 'center',
    },
  });
}
