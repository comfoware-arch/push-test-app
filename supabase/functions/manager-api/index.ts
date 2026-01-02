import { createClient } from "npm:@supabase/supabase-js@2.45.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const GOOGLE_SERVICE_ACCOUNT_JSON = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON")!;
const FCM_PROJECT_ID = Deno.env.get("FCM_PROJECT_ID")!;

if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error("Missing Supabase env");
if (!GOOGLE_SERVICE_ACCOUNT_JSON) throw new Error("Missing GOOGLE_SERVICE_ACCOUNT_JSON");
if (!FCM_PROJECT_ID) throw new Error("Missing FCM_PROJECT_ID");

const serviceAccount = JSON.parse(GOOGLE_SERVICE_ACCOUNT_JSON);
const clientEmail: string = serviceAccount.client_email;
const privateKeyPem: string = serviceAccount.private_key;

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, { auth: { persistSession: false } });

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";

let cachedToken: { access_token: string; exp_ms: number } | null = null;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "content-type, authorization",
      "access-control-allow-methods": "POST, OPTIONS",
    },
  });
}

function normalizeZone(z: unknown) {
  return String(z ?? "").trim().toLowerCase().replace(/\s+/g, "-").slice(0, 64);
}
function parseTableNo(t: unknown) {
  const n = Number.parseInt(String(t), 10);
  return Number.isFinite(n) ? n : NaN;
}

function base64url(bytes: Uint8Array) {
  let s = "";
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  const b64 = btoa(s);
  return b64.replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function base64urlJson(obj: unknown) {
  return base64url(new TextEncoder().encode(JSON.stringify(obj)));
}

async function importPkcs8PrivateKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function signJwtRS256(header: object, payload: object, pemKey: string) {
  const headerPart = base64urlJson(header);
  const payloadPart = base64urlJson(payload);
  const signingInput = `${headerPart}.${payloadPart}`;

  const key = await importPkcs8PrivateKey(pemKey);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(signingInput));
  return `${signingInput}.${base64url(new Uint8Array(sig))}`;
}

async function getAccessToken(): Promise<string> {
  const now = Date.now();
  if (cachedToken && now < cachedToken.exp_ms - 60_000) return cachedToken.access_token;

  const iat = Math.floor(now / 1000);
  const exp = iat + 3600;

  const jwt = await signJwtRS256(
    { alg: "RS256", typ: "JWT" },
    { iss: clientEmail, scope: FCM_SCOPE, aud: OAUTH_TOKEN_URL, iat, exp },
    privateKeyPem,
  );

  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: jwt,
  });

  const resp = await fetch(OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  });

  const data = await resp.json().catch(() => ({} as any));
  if (!resp.ok) throw new Error(`OAuth error ${resp.status}: ${JSON.stringify(data)}`);

  cachedToken = {
    access_token: data.access_token,
    exp_ms: now + (data.expires_in ?? 3600) * 1000,
  };
  return cachedToken.access_token;
}

function isTokenInvalidFcmError(fcmErr: any): boolean {
  const e = fcmErr?.error;
  const status = String(e?.status || "");
  const msg = String(e?.message || "");
  const details = JSON.stringify(e?.details || []);
  return (
    status.includes("NOT_FOUND") ||
    status.includes("INVALID_ARGUMENT") ||
    msg.toLowerCase().includes("unregistered") ||
    details.toLowerCase().includes("unregistered") ||
    msg.toLowerCase().includes("not registered")
  );
}

async function fcmSendToToken(token: string, data: Record<string, string>, notification?: { title: string; body: string }) {
  const accessToken = await getAccessToken();
  const url = `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`;

  const payload: any = { message: { token, data } };
  if (notification) payload.message.notification = notification;

  const resp = await fetch(url, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify(payload),
  });

  const respData = await resp.json().catch(() => ({}));
  if (!resp.ok) throw respData;
  return respData;
}

async function deactivateTokens(tokens: string[]) {
  if (!tokens.length) return;
  await supabase.from("manager_devices").update({ is_active: false }).in("fcm_token", tokens);
}

/**
 * POST /register-device
 * body: { device_id, name, token, platform }
 */
async function handleRegisterDevice(req: Request) {
  const { device_id, name, token, platform = "android" } = await req.json().catch(() => ({}));
  if (!device_id) return json(400, { ok: false, error: "device_id required" });
  if (!token) return json(400, { ok: false, error: "token required" });

  const safeName = typeof name === "string" ? name.trim().slice(0, 80) : null;

  const { error } = await supabase.from("manager_devices").upsert(
    {
      device_id,
      name: safeName,
      fcm_token: token,
      platform,
      is_active: true,
      last_seen_at: new Date().toISOString(),
    },
    { onConflict: "device_id" },
  );

  if (error) return json(500, { ok: false, error: error.message });
  return json(200, { ok: true });
}

/**
 * POST /send-call
 * body: { zone, table }
 * Creates call row, then sends type=call to all active devices.
 * Returns request_id.
 */
async function handleSendCall(req: Request) {
  const body = await req.json().catch(() => ({}));
  const zone = normalizeZone(body.zone);
  const tableNo = parseTableNo(body.table);

  if (!zone) return json(400, { ok: false, error: "zone required" });
  if (!Number.isFinite(tableNo) || tableNo <= 0) return json(400, { ok: false, error: "valid table required" });

  const { data: callRow, error: insErr } = await supabase
    .from("table_calls")
    .insert({ zone, table_no: tableNo })
    .select("id")
    .single();

  if (insErr) return json(500, { ok: false, error: insErr.message });
  const requestId = callRow.id as string;

  const { data: rows, error } = await supabase
    .from("manager_devices")
    .select("fcm_token")
    .eq("is_active", true);

  if (error) return json(500, { ok: false, error: error.message });

  const tokens: string[] = (rows || []).map((r: any) => r.fcm_token).filter(Boolean);
  if (!tokens.length) return json(200, { ok: true, request_id: requestId, sent: 0 });

  const notification = {
    title: "🔔 Se necesita servicio",
    body: `Zona: ${zone} • Mesa: ${tableNo}`,
  };

  const payload = {
    type: "call",
    requestId,
    zone,
    table: String(tableNo),
  };

  const invalid: string[] = [];
  let sent = 0;

  for (const t of tokens) {
    try {
      await fcmSendToToken(t, payload, notification);
      sent++;
    } catch (e: any) {
      if (isTokenInvalidFcmError(e)) invalid.push(t);
    }
  }

  await deactivateTokens(invalid);

  return json(200, { ok: true, request_id: requestId, sent, deactivated_invalid: invalid.length });
}

/**
 * POST /claim-call
 * body: { request_id, device_id, name }
 * Atomic claim via SQL function, then sends type=dismiss to all devices.
 */
async function handleClaimCall(req: Request) {
  const { request_id, device_id, name } = await req.json().catch(() => ({}));
  if (!request_id) return json(400, { ok: false, error: "request_id required" });
  if (!device_id) return json(400, { ok: false, error: "device_id required" });

  const safeName = typeof name === "string" ? name.trim().slice(0, 80) : "";

  // Ensure device exists (best-effort)
  await supabase.from("manager_devices").update({ last_seen_at: new Date().toISOString() }).eq("device_id", device_id);

  // Atomic claim
  const { data: claimRes, error: claimErr } = await supabase.rpc("claim_table_call", {
    p_call_id: request_id,
    p_device_id: device_id,
    p_name: safeName,
  });

  if (claimErr) return json(500, { ok: false, error: claimErr.message });

  const claimed = claimRes?.[0]?.claimed === true;
  const status = claimRes?.[0]?.status ?? "cancelled";

  // Send dismiss to all active devices (including claimer is fine)
  const { data: rows, error } = await supabase
    .from("manager_devices")
    .select("fcm_token")
    .eq("is_active", true);

  if (!error) {
    const tokens: string[] = (rows || []).map((r: any) => r.fcm_token).filter(Boolean);
    const invalid: string[] = [];

    for (const t of tokens) {
      try {
        await fcmSendToToken(t, { type: "dismiss", requestId: request_id });
      } catch (e: any) {
        if (isTokenInvalidFcmError(e)) invalid.push(t);
      }
    }
    await deactivateTokens(invalid);
  }

  if (!claimed) {
    // Someone else already took it
    return json(409, { ok: false, error: "already_taken", status });
  }

  return json(200, { ok: true, status: "taken" });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return json(200, { ok: true });

  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "");

  if (req.method !== "POST") return json(405, { ok: false, error: "method_not_allowed" });

  if (path.endsWith("/register-device")) return await handleRegisterDevice(req);
  if (path.endsWith("/send-call")) return await handleSendCall(req);
  if (path.endsWith("/claim-call")) return await handleClaimCall(req);

  return json(404, { ok: false, error: "not_found" });
});
