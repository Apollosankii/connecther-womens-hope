import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import * as DocumentPicker from 'expo-document-picker';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { TextField } from '@/components/ui/TextField';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import {
  getSignedUrlForMyVerificationDocument,
  listMyVerificationDocuments,
  uploadProviderVerificationDocument,
} from '@/services/api/documents';
import { submitProviderApplication } from '@/services/api/provider';
import { listServices } from '@/services/api/services';
import type { ThemeColors } from '@/theme/types';

type Props = NativeStackScreenProps<AppStackParamList, 'ProviderApplication'>;

function formatLocalSummary(assets: readonly DocumentPicker.DocumentPickerAsset[], empty: string): string {
  if (!assets.length) return empty;
  const lines = assets.map((a) => (a.name ?? 'file').trim() || 'file');
  return `${assets.length} file(s) selected:\n${lines.map((l) => `• ${l}`).join('\n')}`;
}

export function ProviderApplicationScreen({ navigation }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const qc = useQueryClient();
  const [gender, setGender] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [country, setCountry] = useState('');
  const [county, setCounty] = useState('');
  const [areaName, setAreaName] = useState('');
  const [natId, setNatId] = useState('');
  const [emm1, setEmm1] = useState('');
  const [emm2, setEmm2] = useState('');
  const [workingHours, setWorkingHours] = useState('');
  const [professionalTitle, setProfessionalTitle] = useState('');
  const [experience, setExperience] = useState('');
  const [selectedServices, setSelectedServices] = useState<Set<number>>(new Set());
  const [idDocs, setIdDocs] = useState<DocumentPicker.DocumentPickerAsset[]>([]);
  const [certDocs, setCertDocs] = useState<DocumentPicker.DocumentPickerAsset[]>([]);

  const { data } = useQuery({ queryKey: ['services'], queryFn: listServices });
  const services = data ?? [];

  const docsQ = useQuery({
    queryKey: ['documents', 'my-verification'],
    queryFn: listMyVerificationDocuments,
  });

  useFocusEffect(
    useCallback(() => {
      void qc.invalidateQueries({ queryKey: ['documents', 'my-verification'] });
    }, [qc]),
  );

  const selectedIds = useMemo(() => Array.from(selectedServices.values()).sort((a, b) => a - b), [selectedServices]);

  const pickIds = async () => {
    const r = await DocumentPicker.getDocumentAsync({
      type: '*/*',
      multiple: true,
      copyToCacheDirectory: true,
    });
    if (!r.canceled && r.assets?.length) setIdDocs([...r.assets]);
  };

  const pickCerts = async () => {
    const r = await DocumentPicker.getDocumentAsync({
      type: '*/*',
      multiple: true,
      copyToCacheDirectory: true,
    });
    if (!r.canceled && r.assets?.length) setCertDocs([...r.assets]);
  };

  const submit = useMutation({
    mutationFn: async () => {
      if (selectedIds.length === 0) throw new Error('Select at least one service');
      if (idDocs.length === 0 || certDocs.length === 0) {
        throw new Error('Please choose required documents (ID and certification files)');
      }
      for (const a of idDocs) {
        const ok = await uploadProviderVerificationDocument({
          fileUri: a.uri,
          fileName: a.name ?? `id-${Date.now()}.bin`,
          docTypeLabel: 'id',
        });
        if (!ok) throw new Error('Could not upload an ID document. Check connection and try again.');
      }
      for (const a of certDocs) {
        const ok = await uploadProviderVerificationDocument({
          fileUri: a.uri,
          fileName: a.name ?? `cert-${Date.now()}.bin`,
          docTypeLabel: 'certificate',
        });
        if (!ok) throw new Error('Could not upload a certification file. Check connection and try again.');
      }
      return submitProviderApplication({
        gender,
        birthDate,
        country,
        county,
        areaName,
        natId,
        emmCont1: emm1,
        emmCont2: emm2,
        serviceIds: selectedIds,
        workingHours,
        professionalTitle,
        experience,
      });
    },
    onSuccess: async (res) => {
      if (res.ok) {
        await qc.invalidateQueries({ queryKey: ['documents', 'my-verification'] });
        await qc.invalidateQueries({ queryKey: ['profile', 'me'] });
        Alert.alert('Application submitted', "You'll be notified once approved.");
        navigation.goBack();
      } else {
        Alert.alert('Application failed', res.errorCode ?? 'Could not submit');
      }
    },
    onError: (e) => Alert.alert('Application failed', e instanceof Error ? e.message : 'Unknown error'),
  });

  const toggleService = (id: number) => {
    setSelectedServices((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const serverDocs = docsQ.data ?? [];
  const [openingDocId, setOpeningDocId] = useState<number | null>(null);

  async function openMyDocument(docId: number, docTitle: string) {
    if (openingDocId != null) return;
    try {
      setOpeningDocId(docId);
      const signed = await getSignedUrlForMyVerificationDocument(docId);
      if (signed) {
        navigation.navigate('ProviderDocuments', { url: signed, title: docTitle });
      } else {
        Alert.alert('Document', 'Could not open this file. Try again later.');
      }
    } finally {
      setOpeningDocId(null);
    }
  }

  return (
    <Screen padded={false} scroll>
      <View style={styles.root}>
          <View style={styles.header}>
            <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
              <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
            </IconButton>
            <View style={{ flex: 1 }}>
              <AppText variant="h3">Apply as a service provider</AppText>
              <AppText variant="body" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
                Fill in your details, upload verification documents, then submit. An admin will review your application.
              </AppText>
            </View>
          </View>

          <View style={styles.content}>
            <AppCard style={styles.card}>
              <AppText variant="rowTitle">Verification documents</AppText>
              <AppText variant="caption" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
                Required: at least one ID file and one certification file (same as the Android app).
              </AppText>

              <AppButton variant="outline" onPress={() => void pickIds()} style={{ marginTop: 10 }}>
                Choose ID document(s)
              </AppButton>
              <AppText variant="caption" style={{ marginTop: 6, color: colors.onSurface }}>
                {formatLocalSummary(idDocs, 'No ID document selected yet.')}
              </AppText>

              <AppButton variant="outline" onPress={() => void pickCerts()} style={{ marginTop: 12 }}>
                Choose certification file(s)
              </AppButton>
              <AppText variant="caption" style={{ marginTop: 6, color: colors.onSurface }}>
                {formatLocalSummary(certDocs, 'No certification files selected yet.')}
              </AppText>

              <View style={{ height: 16 }} />
              <AppText variant="bodyStrong">Your uploaded files</AppText>
              <AppText variant="caption" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
                Only documents you uploaded are shown here.
              </AppText>
              {docsQ.isLoading ? (
                <AppText variant="caption" style={{ marginTop: 6, color: colors.onSurfaceVariant }}>
                  Loading…
                </AppText>
              ) : docsQ.isError ? (
                <AppText variant="caption" style={{ marginTop: 6, color: colors.onSurfaceVariant }}>
                  Could not load document list.
                </AppText>
              ) : serverDocs.length === 0 ? (
                <AppText variant="caption" style={{ marginTop: 6, color: colors.onSurfaceVariant }}>
                  No verification files on the server yet. They appear here after a successful upload when you submit this
                  form (or if you have submitted before).
                </AppText>
              ) : (
                <View style={{ marginTop: 8, gap: 8 }}>
                  {serverDocs.map((doc) => (
                    <View key={doc.id} style={styles.docRow}>
                      <AppText variant="caption" style={{ flex: 1, color: colors.onSurface }}>
                        {doc.docTypeName}: {doc.fileLabel}
                        {doc.verified ? ' (verified)' : ' (pending review)'}
                      </AppText>
                      <Pressable
                        onPress={() => void openMyDocument(doc.id, doc.docTypeName)}
                        accessibilityRole="button"
                        accessibilityLabel="View document in app"
                        disabled={openingDocId != null}
                      >
                        {openingDocId === doc.id ? (
                          <ActivityIndicator size="small" color={colors.primary} />
                        ) : (
                          <AppText variant="link">View</AppText>
                        )}
                      </Pressable>
                    </View>
                  ))}
                </View>
              )}

              <View style={{ height: 20 }} />
              <TextField label="Gender" value={gender} onChangeText={setGender} />
              <TextField label="Date of birth (YYYY-MM-DD)" value={birthDate} onChangeText={setBirthDate} />
              <TextField label="Country" value={country} onChangeText={setCountry} />
              <TextField label="County" value={county} onChangeText={setCounty} />
              <TextField label="Area / Locality" value={areaName} onChangeText={setAreaName} />
              <TextField label="National ID" value={natId} onChangeText={setNatId} />
              <TextField label="Professional title" value={professionalTitle} onChangeText={setProfessionalTitle} />
              <TextField label="Experience (short)" value={experience} onChangeText={setExperience} />
              <TextField label="Working hours" value={workingHours} onChangeText={setWorkingHours} placeholder="e.g., Mon–Fri 9am–5pm" />
              <TextField label="Emergency contact 1" value={emm1} onChangeText={setEmm1} keyboardType="phone-pad" />
              <TextField label="Emergency contact 2" value={emm2} onChangeText={setEmm2} keyboardType="phone-pad" />

              <View style={{ height: 8 }} />
              <AppText variant="rowTitle">Services you will offer</AppText>
              <AppText variant="caption" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
                Select at least one.
              </AppText>
              <View style={{ height: 8 }} />
              <View style={styles.serviceGrid}>
                {services.map((s, svcIdx) => {
                  const active = selectedServices.has(s.id);
                  return (
                    <Pressable
                      key={`svc-${s.id}-${svcIdx}`}
                      onPress={() => toggleService(s.id)}
                      style={[styles.servicePill, active && styles.servicePillActive]}
                    >
                      <AppText style={[styles.servicePillText, active && styles.servicePillTextActive]}>{s.name}</AppText>
                    </Pressable>
                  );
                })}
              </View>

              <View style={{ height: 12 }} />
              <AppButton onPress={() => submit.mutate()} loading={submit.isPending}>
                Submit application
              </AppButton>
            </AppCard>
          </View>
      </View>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    root: {
      flex: 1,
      backgroundColor: colors.background,
    },
    header: {
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 12,
    },
    content: {
      paddingHorizontal: 16,
      paddingBottom: 16,
    },
    card: {
      borderRadius: 16,
      gap: 12,
    },
    docRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      paddingVertical: 4,
    },
    serviceGrid: {
      flexDirection: 'row',
      flexWrap: 'wrap',
      gap: 10,
    },
    servicePill: {
      paddingHorizontal: 12,
      paddingVertical: 8,
      borderRadius: 999,
      borderWidth: 1,
      borderColor: colors.outline,
      backgroundColor: colors.surface,
    },
    servicePillActive: {
      backgroundColor: colors.primary,
      borderColor: colors.primary,
    },
    servicePillText: {
      color: colors.onSurface,
      fontWeight: '700',
      fontSize: 13,
    },
    servicePillTextActive: {
      color: colors.onPrimary,
    },
  });
}
