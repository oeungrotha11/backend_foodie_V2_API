# Bakong KHQR integration (feature branch)

## Current first step

This branch starts with a backend-only token verification check using the official Bakong Open API. The token must be configured on the Spring Boot server and is never returned to Flutter.

## Backend configuration

Set these environment variables before starting Spring Boot:

- `BAKONG_BASE_URL=https://api-bakong.nbc.gov.kh`
- `BAKONG_API_TOKEN=<the token issued to your registered email>`

Do not commit the real token or put it in Flutter.

## Verify backend token

After logging in to Foodie, call:

```http
POST /api/v1/bakong/verify-token
Authorization: Bearer <Foodie access token>
```

A successful response contains `valid` and `message`; it never includes the Bakong token. This endpoint only verifies Open API credentials. It does not initiate or confirm a customer payment.

## Required before adding a live checkout option

1. Identify the Bakong account ID that will receive the payment (for example, the account ID shown by the bank/Bakong account owner).
2. Confirm the KHQR generation method and currency with the official KHQR SDK documentation.
3. Generate a unique, order-specific KHQR on the backend.
4. Store the expected amount and transaction hash/reference against the order.
5. Verify the transaction with Bakong from the backend and match the paid amount/currency before marking the order as purchased.
6. Add the Flutter QR screen and only clear the cart after server-confirmed payment.

The Open API token by itself does not identify the receiving account and does not make an order paid. Do not treat a deeplink return or a customer-side success screen as proof of payment.

Official references:
- https://api-bakong.nbc.gov.kh/
- https://api-bakong.nbc.gov.kh/Bakong%20Open%20API%20Docs%20v1.0.1.pdf
