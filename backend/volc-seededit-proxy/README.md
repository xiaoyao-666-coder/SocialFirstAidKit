# Volc Image Edit Proxy

This is a small backend proxy for Volcengine Ark image editing. It keeps the Ark API key on the server and exposes a compact endpoint for the Android app.

## Setup

1. Copy `.env.example` to `.env`.
2. Set `ARK_API_KEY` to the Ark API key from Volcengine.
3. Start the service:

```bash
npm start
```

The default listener is `127.0.0.1:8787`.

## Endpoint

`POST /api/image/seededit`

Headers:

```text
Content-Type: application/json
Authorization: Bearer <PROXY_CLIENT_TOKEN>
```

`Authorization` is required when `PROXY_CLIENT_TOKEN` is set. A token is
mandatory whenever `HOST` is configured to a non-loopback address.

Request body:

```json
{
  "prompt": "Edit this photo into a natural social sharing style while preserving the main subject.",
  "image": "data:image/jpeg;base64,...",
  "size": "2K",
  "guidanceScale": 5.5,
  "watermark": false
}
```

`image` can be an `http(s)` URL, a `data:image/...;base64,...` URL, or a bare base64 string.

Response body:

```json
{
  "code": 0,
  "message": "success",
  "trace_id": "server-generated-request-id",
  "data": {
    "images": [
      {
        "url": "https://...",
        "size": "adaptive"
      }
    ],
    "finish_reason": null,
    "usage": null
  }
}
```

## Environment

| Name | Required | Default | Description |
| --- | --- | --- | --- |
| `ARK_API_KEY` | Yes |  | Volcengine Ark API key. |
| `HOST` | No | `127.0.0.1` | Listen address. Non-loopback values require `PROXY_CLIENT_TOKEN`. |
| `PORT` | No | `8787` | Local server port. |
| `ARK_BASE_URL` | No | `https://ark.cn-beijing.volces.com/api/v3` | Ark API base URL. |
| `VOLC_IMAGE_MODEL` | No | `doubao-seedream-5-0-lite-260128` | Image generation/editing model ID. |
| `PROXY_CLIENT_TOKEN` | Conditional |  | Client token. Required for non-loopback listeners. |
| `ALLOWED_ORIGINS` | No |  | Comma-separated CORS allowlist. Empty blocks browser cross-origin access. |
| `REQUEST_TIMEOUT_MS` | No | `120000` | Ark request timeout. |
| `MAX_BODY_BYTES` | No | `15728640` | Max JSON body size. |
