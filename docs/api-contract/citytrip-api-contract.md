# CityTrip Shared API Contract

本文件是 Web、微信小程序、Flutter 共用的轻量契约源。后续如引入 OpenAPI/codegen，应以这里的字段语义为迁移基础。

## Conventions

- Base path: `/api`
- JSON charset: UTF-8
- Auth header: `Authorization: Bearer <jwt>`
- Error body:

```json
{
  "status": 400,
  "message": "validation message",
  "path": "/api/itineraries/smart-fill",
  "traceId": null
}
```

## Auth

### POST `/api/users`

Request:

```json
{
  "username": "alice",
  "password": "strong-password",
  "nickname": "Alice"
}
```

Response `201`:

```json
{
  "id": 101,
  "username": "alice",
  "nickname": "Alice",
  "role": 0,
  "token": "jwt-token"
}
```

### POST `/api/sessions`

Request:

```json
{
  "username": "alice",
  "password": "strong-password"
}
```

Response `200`: same as register response.

### GET `/api/users/me`

Requires auth. Response `200`: current user session payload.

## Itinerary Generation

### POST `/api/itineraries/generate`

Requires auth. Public-compatible legacy path `POST /api/itineraries` uses the same request body and returns `201`.

Request:

```json
{
  "cityName": "成都",
  "cityCode": "chengdu",
  "tripDays": 1,
  "tripDate": "2026-05-01",
  "totalBudget": 500,
  "budgetLevel": "medium",
  "themes": ["人文", "美食"],
  "isRainy": false,
  "isNight": false,
  "walkingLevel": "medium",
  "companionType": "friends",
  "startTime": "09:00",
  "endTime": "18:00",
  "mustVisitPoiNames": ["宽窄巷子"],
  "departurePlaceName": "当前位置",
  "departureLatitude": 30.6573,
  "departureLongitude": 104.0817
}
```

Important validation:

- `tripDays`: 0.5-30 when present
- `tripDate`: `yyyy-MM-dd` when present
- `startTime` / `endTime`: `HH:mm` when present
- `themes`: at most 12 items
- `mustVisitPoiNames`: at most 20 items

Response excerpt:

```json
{
  "id": 601,
  "selectedOptionKey": "balanced",
  "totalDuration": 420,
  "totalCost": 180.0,
  "recommendReason": "...",
  "nodes": [
    {
      "poiId": 1,
      "poiName": "宽窄巷子",
      "stepOrder": 1,
      "startTime": "09:30",
      "endTime": "11:00",
      "segmentRouteGuide": {
        "summary": "步行约 5 分钟",
        "transportMode": "walk",
        "durationMinutes": 5,
        "distanceKm": 0.3
      }
    }
  ],
  "options": [
    {
      "optionKey": "balanced",
      "title": "均衡路线",
      "signature": "1-2-3",
      "nodes": []
    }
  ]
}
```

## Smart Fill

### POST `/api/itineraries/smart-fill`

Request:

```json
{
  "text": "我想明天去 IFS 和太古里，少走路，预算 500"
}
```

Validation: `text` is required and at most 1000 characters.

Response:

```json
{
  "cityName": "成都",
  "themes": ["购物"],
  "mustVisitPoiNames": ["IFS", "太古里"],
  "walkingLevel": "low",
  "totalBudget": 500,
  "summary": ["识别到必去：IFS、太古里"]
}
```

## Chat

### POST `/api/chat/messages`

Requires auth.

Request:

```json
{
  "question": "这条路线下雨天怎么调整？",
  "context": {
    "pageType": "result",
    "cityName": "成都",
    "preferences": ["少走路"],
    "itinerary": {
      "itineraryId": 601,
      "selectedOptionKey": "balanced",
      "summary": "宽窄巷子 -> 太古里",
      "nodes": []
    }
  }
}
```

Validation: `question` is required and at most 2000 characters.

Response:

```json
{
  "answer": "建议把露天点替换为室内点...",
  "relatedTips": [],
  "evidence": ["节点：宽窄巷子", "天气：雨天"],
  "skillPayload": null
}
```

### POST `/api/chat/messages/stream`

Requires auth. SSE payload events use JSON data:

```json
{"type":"token","content":"建议"}
{"type":"meta","relatedTips":[],"evidence":[],"skillPayload":null}
{"type":"done"}
```

## Saved Itinerary Operations

### PUT `/api/itineraries/{id}/favorite`

Requires auth. Request:

```json
{
  "selectedOptionKey": "balanced",
  "title": "我的成都路线"
}
```

### PATCH `/api/itineraries/{id}/public`

Requires auth. Request:

```json
{
  "isPublic": true,
  "title": "适合第一次来成都的路线",
  "shareNote": "少走路版本",
  "selectedOptionKey": "balanced",
  "themes": ["人文", "美食"]
}
```

## Itinerary Edit

### POST `/api/itineraries/{id}/edits/apply`

Requires auth. Request:

```json
{
  "source": "manual",
  "summary": "把太古里提前",
  "operations": [
    {
      "type": "move-node",
      "nodeKey": "poi-2",
      "targetDayNo": 1,
      "targetIndex": 1
    }
  ]
}
```

### POST `/api/itineraries/{id}/edits/restore`

Requires auth. Request:

```json
{
  "versionId": 12
}
```

## Backend Resource Guard

AI-heavy endpoints (`chat`, `stream`, `smart-fill`, `generate`) are protected by backend concurrency and cooldown controls under `app.ai-guard.*`. Clients should treat `409` with a busy message as retryable after a short delay.
