import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEY = '@connecther/emergency_contacts_v1';
const MAX_CONTACTS = 5;

export type EmergencyContact = { id: string; name: string; phone: string };

export async function getEmergencyContacts(): Promise<EmergencyContact[]> {
  const raw = await AsyncStorage.getItem(STORAGE_KEY);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((x): x is EmergencyContact => {
        if (!x || typeof x !== 'object') return false;
        const o = x as Record<string, unknown>;
        return typeof o.id === 'string' && typeof o.name === 'string' && typeof o.phone === 'string';
      })
      .slice(0, MAX_CONTACTS);
  } catch {
    return [];
  }
}

export async function saveEmergencyContacts(contacts: EmergencyContact[]): Promise<void> {
  const trimmed = contacts.slice(0, MAX_CONTACTS).map((c) => ({
    id: c.id.trim(),
    name: c.name.trim(),
    phone: c.phone.trim(),
  }));
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
}

export async function addEmergencyContact(contact: EmergencyContact): Promise<boolean> {
  const list = await getEmergencyContacts();
  if (list.length >= MAX_CONTACTS) return false;
  list.push(contact);
  await saveEmergencyContacts(list);
  return true;
}

export async function removeEmergencyContact(contactId: string): Promise<void> {
  const list = await getEmergencyContacts();
  await saveEmergencyContacts(list.filter((c) => c.id !== contactId));
}
