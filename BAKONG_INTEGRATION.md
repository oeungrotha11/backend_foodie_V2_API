# Bakong KHQR integration (feature branch)

## Configuration

Set these environment variables on the Spring Boot backend only:

- `BAKONG_BASE_URL=https://api-bakong.nbc.gov.kh`
- `BAKONG_API_TOKEN=<token from your Bakong Open API email>`
- `BAKONG_ACCOUNT_ID=<receiving account ID>`
- `BAKONG_TEST_MODE=false` for real production KHQR
- `BAKONG_SAME_NUMERIC_KHR_MODE=true` only when intentionally testing same-number KHR payments

Do not commit credentials, put the API token in Flutter, or store the banking password.

## Backend endpoints

All endpoints require the normal Foodie access token:

- `POST /api/v1/bakong/verify-token` — verifies Open API token configuration.
- `POST /api/v1/bakong/generate-qr` — generates a KHQR payload.
- `POST /api/v1/bakong/check-transaction` — checks a transaction by the KHQR MD5 hash.

Example QR generation request:

```json
{
  "amount": 5.50,
  "currency": "USD",
  "billNumber": "FOODIE-TEST-001",
  "expirationMinutes": 15,
  "merchantName": "Foodie",
  "merchantCity": "PHNOM PENH"
}
```

When `BAKONG_SAME_NUMERIC_KHR_MODE=true` and `BAKONG_TEST_MODE=false`, the service deliberately generates a real KHR KHQR for **5.50 KHR** while returning `displayAmountUsd: 5.50` for the UI. The QR response's `amount` and `currency` describe what the QR actually requests (5.50 KHR); `displayAmountUsd` describes the separate USD number displayed by Foodie. The setting defaults to false and must be explicitly enabled.

**Important financial warning:** this mode does not convert currencies. A displayed `$5.50` order with a `5.50 KHR` QR is underpaid by design. Do not mark the order fully paid, fulfill it, or treat a successful transaction as settlement of the full USD amount. Use this only for a deliberately configured low-value payment test with clear user-facing disclosure, or use a proper USD-to-KHR conversion for actual sales. Confirm fractional-KHR acceptance with the receiving/payment app before relying on values such as 5.50 KHR.

The current integration does not bind a QR to persisted order state. Before production checkout, the backend must create/identify the Foodie order, calculate the owed amount from persisted order data (never trust a client-supplied total), store the QR MD5 and expiry against that order, and only mark an order fully paid after verifying the matching payment and amount/currency. The current `check-transaction` endpoint is diagnostic only, not sufficient to mark orders paid.

The account password is not needed by these endpoints.

Official documentation: https://api-bakong.nbc.gov.kh/Bakong%20Open%20API%20Docs%20v1.0.1.pdf
