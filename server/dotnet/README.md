# Connect direct charges with Checkout (.NET)
An .NET implementation

## Requirements

* [.NET SDK 6.0](https://dotnet.microsoft.com/download/dotnet-core)
* [Configured .env file](../../README.md)

## How to run

1. Confirm `.env` configuration

Ensure the API keys, base price, and webhook secret are configured in `.env` in
this directory. It should include the following keys:

```yaml
# Stripe API keys - see https://stripe.com/docs/development/quickstart#api-keys
STRIPE_PUBLISHABLE_KEY=pk_test...
STRIPE_SECRET_KEY=sk_test...

# Required to verify signatures in the webhook handler.
# See README on how to use the Stripe CLI to test webhooks
STRIPE_WEBHOOK_SECRET=whsec_...

STATIC_DIR=../../client
DOMAIN=http://localhost:4242
BASE_PRICE=1000
```

2. Run the application from the terminal

```bash
dotnet run
```

3. Go to `localhost:4242` to see the demo.
