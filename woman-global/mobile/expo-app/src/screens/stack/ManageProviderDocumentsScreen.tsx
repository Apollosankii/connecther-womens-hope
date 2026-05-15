import { MaterialCommunityIcons } from '@expo/vector-icons';
import * as DocumentPicker from 'expo-document-picker';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import { useTheme } from '@/providers/ThemeProvider';
import {
  getSignedUrlForMyVerificationDocument,
  listMyVerificationDocuments,
  uploadProviderVerificationDocument,
} from '@/services/api/documents';
import type { ThemeColors } from '@/theme/types';

export function ManageProviderDocumentsScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const qc = useQueryClient();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['provider-documents', 'mine'],
    queryFn: listMyVerificationDocuments,
  });

  const uploadMut = useMutation({
    mutationFn: uploadProviderVerificationDocument,
    onSuccess: async () => qc.invalidateQueries({ queryKey: ['provider-documents'] }),
  });

  async function pickAndUpload() {
    const res = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true, multiple: false });
    if (res.canceled) return;
    const file = res.assets?.[0];
    if (!file?.uri) return;
    const name = file.name || 'document';
    await uploadMut.mutateAsync({ fileUri: file.uri, fileName: name, docTypeLabel: name });
  }

  const items = data ?? [];
  const [openingDocId, setOpeningDocId] = useState<number | null>(null);

  async function openDocument(docId: number, docTitle: string) {
    if (openingDocId != null) return;
    try {
      setOpeningDocId(docId);
      const signed = await getSignedUrlForMyVerificationDocument(docId);
      if (signed) {
        navigation.navigate('ProviderDocuments', { url: signed, title: docTitle });
      } else {
        Alert.alert('Document', 'Could not open this file.');
      }
    } finally {
      setOpeningDocId(null);
    }
  }

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">My Documents</AppText>
        <View style={{ flex: 1 }} />
        <IconButton
          variant="filled"
          accessibilityLabel="Upload"
          onPress={uploadMut.isPending ? () => {} : pickAndUpload}
          style={uploadMut.isPending ? { opacity: 0.6 } : undefined}
        >
          <MaterialCommunityIcons name="upload" size={20} color={colors.onPrimary} />
        </IconButton>
      </View>
      <View style={styles.divider} />
      {isLoading ? <Spinner /> : null}
      {error ? (
        <AppText style={styles.error} onPress={() => refetch()}>
          Failed to load documents. Tap to retry.
        </AppText>
      ) : null}
      <FlatList
        data={items}
        keyExtractor={(d) => String(d.id)}
        contentContainerStyle={styles.list}
        ListEmptyComponent={isLoading ? null : <ListEmpty title="No documents yet" body="Upload your credentials/verification documents here." />}
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            onPress={() => void openDocument(item.id, item.docTypeName)}
            disabled={openingDocId != null}
          >
            <AppCard padding="sm" style={styles.card}>
              <View style={styles.cardRow}>
                <View style={{ flex: 1 }}>
                  <AppText variant="rowTitle">{item.docTypeName}</AppText>
                  <AppText variant="caption" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
                    {item.fileLabel}
                  </AppText>
                  <AppText variant="caption" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
                    {item.verified ? 'Verified' : 'Pending review'}
                  </AppText>
                </View>
                {openingDocId === item.id ? (
                  <ActivityIndicator size="small" color={colors.primary} />
                ) : (
                  <MaterialCommunityIcons name="chevron-right" size={22} color={colors.onSurfaceVariant} />
                )}
              </View>
            </AppCard>
          </Pressable>
        )}
      />
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      backgroundColor: colors.background,
    },
    divider: {
      height: 1,
      marginHorizontal: 20,
      backgroundColor: colors.outline,
    },
    error: {
      color: colors.bookingStatus.declinedText,
      marginHorizontal: 20,
      marginBottom: 8,
      fontWeight: '600',
    },
    list: {
      paddingHorizontal: 12,
      paddingTop: 12,
      paddingBottom: 16,
      gap: 10,
    },
    card: { borderRadius: 16 },
    cardRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
    },
    meta: { marginTop: 4 },
  });
}
