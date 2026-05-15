import * as SMS from 'expo-sms';

function buildMessage(displayName: string, lat: number | null, lng: number | null): string {
  const locationText =
    lat != null && lng != null ? `Location: https://maps.google.com/?q=${lat},${lng}` : 'Location: unavailable';
  return (
    `EMERGENCY ALERT: ${displayName} needs help! ` +
    `This is a GBV emergency alert from the ConnectHer app. ` +
    `${locationText}. Please respond immediately or call emergency services.`
  );
}

/** On-device SMS to emergency numbers (non-subscriber path or when server SMS is disabled). */
export async function sendGbvDeviceSms(params: {
  phones: string[];
  displayName: string;
  latitude: number | null;
  longitude: number | null;
}): Promise<{ ok: boolean; reason?: string }> {
  const phones = params.phones.map((p) => p.trim()).filter(Boolean);
  if (!phones.length) return { ok: false, reason: 'no_phones' };

  const available = await SMS.isAvailableAsync();
  if (!available) return { ok: false, reason: 'sms_unavailable' };

  const message = buildMessage(params.displayName, params.latitude, params.longitude);
  const result = await SMS.sendSMSAsync(phones, message);
  if (result.result === 'sent') return { ok: true };
  if (result.result === 'cancelled') return { ok: false, reason: 'cancelled' };
  return { ok: false, reason: String(result.result) };
}
