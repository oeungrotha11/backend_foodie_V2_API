# Bakong KHQR integration (feature branch)

## Configuration

Set these environment variables on the Spring Boot backend only:

- `BAKONG_BASE_URL=https://api-bakong.nbc.gov.kh`
- `BAKONG_API_TOKEN=<token from your Bakong Open API email>`
- `BAKONG_ACCOUNT_ID=<receiving account ID>`

Do not commit credentials, put the API token in Flutter, or store the banking password.

## Backend endpoints

All endpoints require the normal Foodie access token:

- `POST /api/v1/bakong/verify-token` — verifies Open API token configuration.
- `POST /api/v1/bakong/generate-qr` — generates a KHQR payload for development/integration testing.
- `POST /api/v1/bakong/check-transaction` — checks a transaction by the KHQR MD5 hash.

Example QR generation request:

```json
{
  "amount": 1.00,
  "currency": "KHR",
  "billNumber": "FOODIE-TEST-001",
  "expirationMinutes": 15,
  "merchantName": "Foodie",
  "merchantCity": "PHNOM PENH"
}
```

This is the initial integration layer only. Before enabling it in checkout, the backend must create/identify the Foodie order, calculate the amount from persisted order data (never trust a client-supplied total), store the QR MD5 and expiration against that order, and only mark the order purchased after Bakong confirms the matching payment. The current `check-transaction` endpoint is a diagnostic building block, not yet sufficient to mark orders paid.

The account password is not needed by these endpoints.

Official documentation: https://api-bakong.nbc.gov.kh/Bakong%20Open%20API%20Docs%20v1.0.1.pdf
