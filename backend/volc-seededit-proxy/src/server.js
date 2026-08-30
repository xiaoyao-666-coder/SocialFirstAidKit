import http from "node:http";
import { randomUUID } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const DEFAULT_PORT = 8787;
const DEFAULT_HOST = "127.0.0.1";
const DEFAULT_ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
const DEFAULT_MODEL = "doubao-seedream-5-0-lite-260128";
const DEFAULT_TIMEOUT_MS = 120_000;
const DEFAULT_MAX_BODY_BYTES = 15 * 1024 * 1024;

loadLocalEnv();

const config = {
  host: process.env.HOST?.trim() || DEFAULT_HOST,
  port: readIntegerEnv("PORT", DEFAULT_PORT),
  arkApiKey: process.env.ARK_API_KEY?.trim() ?? "",
  arkBaseUrl: trimTrailingSlash(process.env.ARK_BASE_URL?.trim() || DEFAULT_ARK_BASE_URL),
  model: process.env.VOLC_IMAGE_MODEL?.trim() ||
    process.env.VOLC_SEEDEDIT_MODEL?.trim() ||
    DEFAULT_MODEL,
  proxyClientToken: process.env.PROXY_CLIENT_TOKEN?.trim() || "",
  allowedOrigins: parseAllowedOrigins(process.env.ALLOWED_ORIGINS),
  timeoutMs: readIntegerEnv("REQUEST_TIMEOUT_MS", DEFAULT_TIMEOUT_MS),
  maxBodyBytes: readIntegerEnv("MAX_BODY_BYTES", DEFAULT_MAX_BODY_BYTES)
};

const server = http.createServer(async (req, res) => {
  const traceId = randomUUID();
  applyCors(req, res);

  if (req.method === "OPTIONS") {
    sendNoContent(res);
    return;
  }

  try {
    if (req.method === "GET" && req.url === "/health") {
      sendJson(res, 200, {
        ok: true,
        service: "volc-image-edit-proxy",
        model: config.model
      });
      return;
    }

    if (req.method === "POST" && req.url === "/api/image/seededit") {
      await handleSeedEdit(req, res, traceId);
      return;
    }

    sendJson(res, 404, errorResponse(traceId, "Not found"));
  } catch (error) {
    if (error instanceof HttpError) {
      sendJson(res, error.statusCode, errorResponse(traceId, error.message));
      return;
    }

    console.error(`[${traceId}] unexpected error`, toSafeErrorLog(error));
    sendJson(res, 500, errorResponse(traceId, "Internal server error"));
  }
});

if (!config.proxyClientToken && !isLoopbackHost(config.host)) {
  throw new Error("PROXY_CLIENT_TOKEN is required when HOST is not a loopback address");
}

server.listen(config.port, config.host, () => {
  console.log(`Volc image edit proxy listening on http://${config.host}:${config.port}`);
  if (!config.arkApiKey) {
    console.warn("ARK_API_KEY is not set. /api/image/seededit will reject requests.");
  }
});

async function handleSeedEdit(req, res, traceId) {
  if (!config.arkApiKey) {
    sendJson(res, 500, errorResponse(traceId, "ARK_API_KEY is not configured"));
    return;
  }

  if (!isAuthorized(req)) {
    sendJson(res, 401, errorResponse(traceId, "Unauthorized"));
    return;
  }

  if (!isJsonRequest(req)) {
    sendJson(res, 415, errorResponse(traceId, "Content-Type must be application/json"));
    return;
  }

  const body = await readJsonBody(req, config.maxBodyBytes);
  const validationError = validateSeedEditRequest(body);
  if (validationError) {
    sendJson(res, 400, errorResponse(traceId, validationError));
    return;
  }

  const arkPayload = buildArkPayload(body);
  const arkResult = await callArkImageGeneration(arkPayload, traceId);

  if (!arkResult.ok) {
    sendJson(res, arkResult.status, errorResponse(traceId, arkResult.message));
    return;
  }

  sendJson(res, 200, normalizeArkResponse(arkResult.data, traceId, arkPayload.size));
}

function buildArkPayload(body) {
  const payload = {
    model: body.model?.trim() || config.model,
    prompt: body.prompt.trim(),
    image: normalizeImageInput(body.image),
    size: body.size?.trim() || "adaptive",
    watermark: typeof body.watermark === "boolean" ? body.watermark : false
  };

  if (!isSeedream5Model(payload.model)) {
    payload.guidance_scale = typeof body.guidanceScale === "number" ? body.guidanceScale : 5.5;
  }

  if (typeof body.seed === "number") {
    payload.seed = body.seed;
  }

  if (body.responseFormat === "b64_json") {
    payload.response_format = "b64_json";
  } else {
    payload.response_format = "url";
  }

  return payload;
}

async function callArkImageGeneration(payload, traceId) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);

  try {
    const response = await fetch(`${config.arkBaseUrl}/images/generations`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${config.arkApiKey}`,
        "Content-Type": "application/json",
        "X-Request-Id": traceId
      },
      body: JSON.stringify(payload),
      signal: controller.signal
    });

    const text = await response.text();
    const data = parseJsonOrNull(text);

    if (!response.ok) {
      console.error(`[${traceId}] ark error`, {
        status: response.status,
        body: sanitizeProviderError(data ?? text)
      });

      return {
        ok: false,
        status: mapProviderStatus(response.status),
        message: extractProviderMessage(data) || `Ark request failed with status ${response.status}`
      };
    }

    if (!data) {
      return {
        ok: false,
        status: 502,
        message: "Ark returned non-JSON response"
      };
    }

    return {
      ok: true,
      data
    };
  } catch (error) {
    if (error?.name === "AbortError") {
      return {
        ok: false,
        status: 504,
        message: "Ark request timed out"
      };
    }

    console.error(`[${traceId}] ark network error`, toSafeErrorLog(error));
    return {
      ok: false,
      status: 502,
      message: "Failed to call Ark image generation API"
    };
  } finally {
    clearTimeout(timeout);
  }
}

function normalizeArkResponse(arkData, traceId, requestedSize) {
  const images = Array.isArray(arkData.data)
    ? arkData.data.map((item) => ({
        url: item.url ?? item.b64_json ?? "",
        size: item.size ?? requestedSize ?? "adaptive"
      })).filter((item) => item.url)
    : [];

  return {
    code: images.length > 0 ? 0 : -1,
    message: images.length > 0 ? "success" : "No image returned",
    trace_id: traceId,
    data: {
      images,
      finish_reason: arkData.finish_reason ?? null,
      usage: arkData.usage ?? null
    }
  };
}

function validateSeedEditRequest(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return "Request body must be a JSON object";
  }

  if (typeof body.prompt !== "string" || body.prompt.trim().length === 0) {
    return "prompt is required";
  }

  if (body.prompt.length > 4000) {
    return "prompt is too long";
  }

  if (typeof body.image !== "string" || body.image.trim().length === 0) {
    return "image is required";
  }

  if (!isSupportedImageInput(body.image)) {
    return "image must be an http(s) URL, data:image base64 URL, or bare base64 string";
  }

  if (body.size !== undefined && (typeof body.size !== "string" || body.size.trim().length === 0)) {
    return "size must be a non-empty string";
  }

  if (body.guidanceScale !== undefined) {
    if (typeof body.guidanceScale !== "number" || body.guidanceScale < 1 || body.guidanceScale > 10) {
      return "guidanceScale must be a number between 1 and 10";
    }
  }

  if (body.watermark !== undefined && typeof body.watermark !== "boolean") {
    return "watermark must be a boolean";
  }

  if (body.seed !== undefined && (!Number.isInteger(body.seed) || body.seed < 0)) {
    return "seed must be a non-negative integer";
  }

  return null;
}

function readJsonBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    let total = 0;
    const chunks = [];

    req.on("data", (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(new HttpError(413, "Request body is too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });

    req.on("end", () => {
      const rawBody = Buffer.concat(chunks).toString("utf8");
      try {
        resolve(rawBody ? JSON.parse(rawBody) : {});
      } catch {
        reject(new HttpError(400, "Invalid JSON body"));
      }
    });

    req.on("error", (error) => {
      reject(error);
    });
  }).catch((error) => {
    if (error instanceof HttpError) {
      throw error;
    }
    throw new HttpError(400, "Failed to read request body");
  });
}

function isAuthorized(req) {
  if (!config.proxyClientToken) {
    return isLoopbackAddress(req.socket.remoteAddress);
  }

  const authorization = req.headers.authorization ?? "";
  const tokenFromBearer = authorization.startsWith("Bearer ")
    ? authorization.slice("Bearer ".length).trim()
    : "";
  const tokenFromHeader = req.headers["x-proxy-token"] ?? "";

  return tokenFromBearer === config.proxyClientToken || tokenFromHeader === config.proxyClientToken;
}

function isJsonRequest(req) {
  const contentType = req.headers["content-type"] ?? "";
  return contentType.toLowerCase().includes("application/json");
}

function normalizeImageInput(image) {
  const value = image.trim();
  if (isHttpUrl(value) || value.toLowerCase().startsWith("data:image/")) {
    return value;
  }
  return `data:image/jpeg;base64,${value}`;
}

function isSupportedImageInput(image) {
  const value = image.trim();
  return isHttpUrl(value) ||
    value.toLowerCase().startsWith("data:image/") ||
    looksLikeBase64(value);
}

function looksLikeBase64(value) {
  return value.length > 32 && /^[A-Za-z0-9+/=\r\n]+$/.test(value);
}

function isHttpUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function isSeedream5Model(model) {
  return model.toLowerCase().includes("seedream-5");
}

function applyCors(req, res) {
  const origin = req.headers.origin;
  const allowOrigin = chooseAllowedOrigin(origin);

  if (allowOrigin) {
    res.setHeader("Access-Control-Allow-Origin", allowOrigin);
    res.setHeader("Vary", "Origin");
  }

  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Proxy-Token");
  res.setHeader("Access-Control-Max-Age", "86400");
}

function chooseAllowedOrigin(origin) {
  if (!origin) {
    return "*";
  }

  if (config.allowedOrigins.length === 0) {
    return "";
  }

  return config.allowedOrigins.includes(origin) ? origin : "";
}

function isLoopbackHost(host) {
  return host === "127.0.0.1" || host === "::1" || host.toLowerCase() === "localhost";
}

function isLoopbackAddress(address) {
  return address === "127.0.0.1" || address === "::1" || address === "::ffff:127.0.0.1";
}

function sendJson(res, statusCode, payload) {
  res.statusCode = statusCode;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(payload));
}

function sendNoContent(res) {
  res.statusCode = 204;
  res.end();
}

function errorResponse(traceId, message) {
  return {
    code: -1,
    message,
    trace_id: traceId,
    data: null
  };
}

function readIntegerEnv(name, fallback) {
  const rawValue = process.env[name];
  if (!rawValue) {
    return fallback;
  }

  const value = Number.parseInt(rawValue, 10);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function loadLocalEnv() {
  const envPath = resolve(process.cwd(), ".env");
  if (!existsSync(envPath)) {
    return;
  }

  const content = readFileSync(envPath, "utf8");
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }

    const equalsIndex = trimmed.indexOf("=");
    if (equalsIndex === -1) {
      continue;
    }

    const key = trimmed.slice(0, equalsIndex).trim();
    const rawValue = trimmed.slice(equalsIndex + 1).trim();
    if (!key || process.env[key] !== undefined) {
      continue;
    }

    process.env[key] = unquoteEnvValue(rawValue);
  }
}

function unquoteEnvValue(value) {
  if (
    (value.startsWith("\"") && value.endsWith("\"")) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  return value;
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, "");
}

function parseAllowedOrigins(value) {
  return (value ?? "")
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean);
}

function parseJsonOrNull(text) {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

function extractProviderMessage(data) {
  if (!data || typeof data !== "object") {
    return "";
  }

  if (typeof data.message === "string") {
    return data.message;
  }

  if (data.error && typeof data.error.message === "string") {
    return data.error.message;
  }

  return "";
}

function sanitizeProviderError(value) {
  if (typeof value === "string") {
    return value.slice(0, 1000);
  }

  if (!value || typeof value !== "object") {
    return value;
  }

  const clone = JSON.parse(JSON.stringify(value));
  delete clone.api_key;
  delete clone.authorization;
  return clone;
}

function toSafeErrorLog(error) {
  return {
    name: error?.name,
    message: error?.message,
    stack: error?.stack?.split("\n").slice(0, 3).join("\n")
  };
}

function mapProviderStatus(status) {
  if (status === 401 || status === 403) {
    return 502;
  }
  if (status === 429) {
    return 429;
  }
  if (status >= 500) {
    return 502;
  }
  return 400;
}

class HttpError extends Error {
  constructor(statusCode, message) {
    super(message);
    this.statusCode = statusCode;
  }
}

process.on("uncaughtException", (error) => {
  console.error("uncaught exception", toSafeErrorLog(error));
});

process.on("unhandledRejection", (reason) => {
  console.error("unhandled rejection", toSafeErrorLog(reason));
});
