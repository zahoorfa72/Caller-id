import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

function respond(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function normalizePhone(input: unknown): string | null {
  if (typeof input !== "string") return null;
  const trimmed = input.trim();
  const value = trimmed.replace(/[\s().-]/g, "");
  if (!/^\+[1-9][0-9]{7,14}$/.test(value)) return null;
  return value;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return respond(405, { message: "Use POST for this endpoint." });

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !anonKey || !serviceKey) {
    return respond(500, { message: "Server configuration is incomplete." });
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  if (!token) return respond(401, { message: "Sign-in is required." });

  const authClient = createClient(supabaseUrl, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: "Bearer " + token } },
  });
  const { data: userData, error: userError } = await authClient.auth.getUser(token);
  if (userError || !userData.user) return respond(401, { message: "Your session is invalid. Please try again." });
  const userId = userData.user.id;

  let payload: { action?: unknown; phone?: unknown; reason?: unknown };
  try {
    payload = await req.json();
  } catch {
    return respond(400, { message: "Request body must be valid JSON." });
  }
  const action = payload.action;
  if (action !== "lookup" && action !== "report_spam") {
    return respond(400, { message: "Unsupported action." });
  }
  const phone = normalizePhone(payload.phone);
  if (!phone) return respond(400, { message: "Enter a phone number in international format, for example +923001234567." });

  const service = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Per-account rate limiting. This is one layer; production should add edge/WAF
  // controls as well because anonymous accounts can be recreated.
  const hourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  const { count, error: countError } = await service
    .from("caller_lookup_events")
    .select("id", { count: "exact", head: true })
    .eq("user_id", userId)
    .gte("created_at", hourAgo);
  if (countError) return respond(500, { message: "Could not enforce lookup limits." });
  if ((count ?? 0) >= 30) return respond(429, { message: "Hourly lookup limit reached. Please try later." });

  const eventAction = action === "lookup" ? "lookup" : "report_spam";
  const { error: eventError } = await service.from("caller_lookup_events").insert({
    user_id: userId,
    action: eventAction,
  });
  if (eventError) return respond(500, { message: "Could not record request." });

  if (action === "report_spam") {
    if (payload.reason !== "spam") return respond(400, { message: "Unsupported report reason." });
    const { error: reportError } = await service.from("caller_reports").insert({
      user_id: userId,
      phone_e164: phone,
      reason: "spam",
    });
    if (reportError) return respond(500, { message: "Report could not be saved." });
    return respond(200, { ok: true, message: "Spam report submitted for review." });
  }

  const { data: record, error: lookupError } = await service
    .from("caller_directory")
    .select("label, category, verified, report_count")
    .eq("phone_e164", phone)
    .maybeSingle();

  if (lookupError) return respond(500, { message: "Caller directory lookup failed." });
  // Only verified public business entries or repeatedly reported spam labels
  // can be surfaced. A report never automatically publishes an identity.
  if (!record || (record.category === "business" && record.verified !== true) ||
      (record.category === "spam" && Number(record.report_count ?? 0) < 3)) {
    return respond(200, {
      found: false,
      message: "No public caller information was found. This does not mean the number is safe or unsafe.",
    });
  }

  return respond(200, {
    found: true,
    record: {
      label: record.category === "spam" ? "Reported spam caller" : record.label,
      category: record.category,
    },
  });
});
