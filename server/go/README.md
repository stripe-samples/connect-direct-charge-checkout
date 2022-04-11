# Connect direct charges with Checkout

## Requirements

- Go 1.13
- [Configured .env file](../../README.md)

## How to run

1. Confirm `.env` configuration

This sample requires a Price in the `BASE_PRICE` environment variable.

Open `.env` and confirm `PRICE` is set equal to the ID of a Price from your
Stripe account. It should look something like:

```yml
BASE_PRICE=1000
```

2. Install dependencies

From the server directory (the one with `server.go`) run:

```sh
go mod tidy
go mod vendor
```

3. Run the application

Again from the server directory run:

```sh
go run server.go
```

4. If you're using the html client, go to `localhost:4242` to see the demo. For
   react, visit `localhost:3000`.
