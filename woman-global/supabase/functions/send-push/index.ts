// Send Web Push notifications using VAPID keys.
// Set in Supabase Dashboard → Edge Functions → send-push → Secrets:
//   WEBPUSH_VAPID_PUBLIC_KEY  (optional; default below)
//   WEBPUSH_VAPID_PRIVATE_KEY (required for sending)

const DEFAULT_VAPID_PUBLIC_KEY =
  "BJC1VoTYWSv1EZ7TeJq5uYsOpj0qAXU_BDcyRCXt6OprmMPpGy-7j0sDi91j9FXkqLdFU77xUyh325EmqLhuG9A";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface PushSubscriptionJSON {
  endpoint: string;
  keys: { p256dh: string; auth: string };
  expirationTime?: number | null;
}

interface SendPushBody {
  subscription: PushSubscriptionJSON;
  title?: string;
  body?: string;
  data?: Record<string, unknown>;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ error: "Method not allowed" }),
      { status: 405, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const publicKey =
    Deno.env.get("WEBPUSH_VAPID_PUBLIC_KEY")?.trim() || DEFAULT_VAPID_PUBLIC_KEY;
  const privateKey = Deno.env.get("WEBPUSH_VAPID_PRIVATE_KEY")?.trim();
  if (!privateKey) {
    return new Response(
      JSON.stringify({
        error:
          "WEBPUSH_VAPID_PRIVATE_KEY is not set. Add it in Supabase Dashboard → Edge Functions → send-push → Secrets.",
      }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  let body: SendPushBody;
  try {
    body = await req.json();
  } catch {
    return new Response(
      JSON.stringify({ error: "Invalid JSON body" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const { subscription, title = "Notification", body: messageBody = "", data = {} } = body;
  if (!subscription?.endpoint || !subscription?.keys?.p256dh || !subscription?.keys?.auth) {
    return new Response(
      JSON.stringify({
        error:
          "Body must include subscription: { endpoint, keys: { p256dh, auth } }, and optional title, body, data.",
      }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  try {
    const webpush = await import("npm:web-push@3.6.7");
    webpush.default.setVapidDetails(
      "mailto:support@connecther.com",
      publicKey,
      privateKey
    );

    const payload = JSON.stringify({
      title,
      body: messageBody,
      ...data,
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await webpush.default.sendNotification(subscription as any, payload, { TTL: 86400 });

    return new Response(
      JSON.stringify({ success: true, message: "Push sent" }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return new Response(
      JSON.stringify({ error: "Failed to send push", detail: message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
