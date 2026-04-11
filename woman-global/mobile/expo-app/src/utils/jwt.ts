import { jwtDecode } from 'jwt-decode';

type JwtPayload = { exp?: number };

export function isJwtExpired(jwt: string, nowMs = Date.now()) {
  try {
    const p = jwtDecode<JwtPayload>(jwt);
    if (!p.exp) return true;
    return p.exp * 1000 <= nowMs;
  } catch {
    return true;
  }
}

export function isJwtExpiringSoon(jwt: string, bufferSeconds: number) {
  try {
    const p = jwtDecode<JwtPayload>(jwt);
    if (!p.exp) return true;
    const bufferMs = bufferSeconds * 1000;
    return p.exp * 1000 <= Date.now() + bufferMs;
  } catch {
    return true;
  }
}

