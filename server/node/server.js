// Replace if using a different env file or config
require("dotenv").config();
const bodyParser = require("body-parser");
const express = require("express");
const { resolve } = require("path");
const session = require("express-session");
const stripe = require("stripe")(process.env.STRIPE_SECRET_KEY);

const app = express();
const port = process.env.PORT || 4242;

app.use(express.static(process.env.STATIC_DIR));
app.use(session({
  secret: "Set this to a random string that is kept secure",
  resave: false,
  saveUninitialized: true,
}))

// Use JSON parser for all non-webhook routes
app.use((req, res, next) => {
  if (req.originalUrl === "/webhook") {
    next();
  } else {
    bodyParser.json()(req, res, next);
  }
});

app.get("/", (req, res) => {
  const path = resolve(process.env.STATIC_DIR + "/index.html");
  res.sendFile(path);
});

// Take a 10% cut.
const calculateApplicationFeeAmount = (basePrice, quantity) => .1 * basePrice * quantity;

app.post('/create-checkout-session', async (req, res) => {
  const domainURL = process.env.DOMAIN;

  const { quantity, account } = req.body;
  const basePrice = process.env.BASE_PRICE
  // Create new Checkout Session for the order
  // For full details see https://stripe.com/docs/api/checkout/sessions/create
  const session = await stripe.checkout.sessions.create({
    payment_method_types: ['card'],
    line_items: [
      {
        name: 'Guitar lesson',
        images: ['https://i.ibb.co/2PNy7yB/guitar.png'],
        quantity: quantity,
        currency: 'USD',
        amount: basePrice, // Keep the amount on the server to prevent customers from manipulating on client
      },
    ],
    payment_intent_data: {
      application_fee_amount: calculateApplicationFeeAmount(basePrice, quantity),
    },
    // ?session_id={CHECKOUT_SESSION_ID} means the redirect will have the session ID set as a query param
    success_url: `${domainURL}/success.html?session_id={CHECKOUT_SESSION_ID}`,
    cancel_url: `${domainURL}/canceled.html`,
  }, {
    stripe_account: account,
  });

  res.send({
    sessionId: session.id,
  });
});

app.get('/config', (req, res) => {
  stripe.accounts.list(
    {limit: 10},
    function(err, accounts) {
      if (err) {
        return res.status(500).send({
          error: err.message
        });
      }
      return res.send({
        accounts,
        publicKey: process.env.STRIPE_PUBLISHABLE_KEY,
        basePrice: process.env.BASE_PRICE,
        currency: process.env.CURRENCY,
      });
    }
  );
});

app.get("/express-dashboard-link", async (req, res) => {
  stripe.accounts.createLoginLink(
    req.query.account_id,
    {redirect_url: req.headers.referer},
    function(err, loginLink) {
      if (err) {
        return res.status(500).send({
          error: err.message
        });
      }
      return res.send({url: loginLink.url});
    }
  );
});

app.post('/webhook', bodyParser.raw({type: 'application/json'}), (req, res) => {
  const sig = req.headers['stripe-signature'];

  let event;

  // Verify webhook signature and extract the event.
  // See https://stripe.com/docs/webhooks/signatures for more information.
  try {
    event = stripe.webhooks.constructEvent(req.body, sig, process.env.STRIPE_WEBHOOK_SECRET);
  } catch (err) {
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  if (event.type === 'checkout.session.completed') {
    const session = event.data.object;
    const connectedAccountId = event.account;
    handleCheckoutSession(connectedAccountId, session);
  }

  response.json({received: true});
});

const handleCheckoutSession = (connectedAccountId, session) => {
    // Fulfill the purchase.
  console.log('Connected account ID: ' + connectedAccountId);
  console.log('Session: ' + JSON.stringify(session));
}

app.listen(port, () => console.log(`Node server listening on port ${port}!`));
