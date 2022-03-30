<?php
use Slim\Http\Request;
use Slim\Http\Response;
use Stripe\Stripe;

require 'vendor/autoload.php';

$dotenv = Dotenv\Dotenv::create(__DIR__);
$dotenv->load();

require './config.php';

$app = new \Slim\App;

// Instantiate the logger as a dependency
$container = $app->getContainer();
$container['logger'] = function ($c) {
  $settings = $c->get('settings')['logger'];
  $logger = new Monolog\Logger($settings['name']);
  $logger->pushProcessor(new Monolog\Processor\UidProcessor());
  $logger->pushHandler(new Monolog\Handler\StreamHandler(__DIR__ . '/logs/app.log', \Monolog\Logger::DEBUG));
  return $logger;
};

$app->add(function ($request, $response, $next) {
    Stripe::setApiKey(getenv('STRIPE_SECRET_KEY'));
    return $next($request, $response);
});

function calculateOrderAmount($items) {
	// Replace this constant with a calculation of the order's amount
	// Calculate the order total on the server to prevent
	// people from directly manipulating the amount on the client
	return 1400;
}

function calculateApplicationFeeAmount($base_price, $quantity) {
  // Take a 10% cut.
	return 0.1 * $base_price * $quantity;
}


$app->post('/create-checkout-session', function(Request $request, Response $response, array $args) {
  $domain_url = getenv('DOMAIN');
  $base_price = getenv('BASE_PRICE');
  $body = json_decode($request->getBody());
  $quantity = $body->quantity;

  // Create new Checkout Session for the order
  // For full details see https://stripe.com/docs/api/checkout/sessions/create
  $checkout_session = \Stripe\Checkout\Session::create([
    'payment_method_types' => ['card'],
    'line_items' => [[
      'name' => 'Guitar lesson',
      'images' => ['https://i.ibb.co/2PNy7yB/guitar.png'],
      'quantity' => $quantity,
      'amount' => $base_price,
      'currency' => 'USD',
    ]],
    'payment_intent_data' => [
      'application_fee_amount' => calculateApplicationFeeAmount($base_price, $quantity),
    ],
    // ?session_id={CHECKOUT_SESSION_ID} means the redirect will have the session ID set as a query para
    'success_url' => $domain_url . '/success.html?session_id={CHECKOUT_SESSION_ID}',
    'cancel_url' => $domain_url . '/canceled.html',
  ], ['stripe_account' => $body->account]);

  return $response->withJson(array('sessionId' => $checkout_session['id']));
});

$app->get('/config', function (Request $request, Response $response, array $args) {
  $accounts = \Stripe\Account::all(['limit' => 10]);
  $pub_key = getenv('STRIPE_PUBLISHABLE_KEY');
  $base_price = getenv('BASE_PRICE');
  $currency = getenv('CURRENCY');
  return $response->withJson([
    'publicKey' => $pub_key,
    'basePrice' => $base_price,
    'currency' => $currency,
    'accounts' => $accounts,
  ]);
});

$app->get('/express-dashboard-link', function (Request $request, Response $response, array $args) {
  $account_id = $request->getQueryParam('account_id');
  $link = \Stripe\Account::createLoginLink(
    $account_id,
    ['redirect_url' => $request->getUri()->getBaseUrl()]
  );
  return $response->withJson(array('url' => $link->url));
});

$app->post('/webhook', function ($request, $response, $next) {
  $payload = $request->getBody();
  $sig_header = $request->getHeaderLine('stripe-signature');

  $event = null;

  // Verify webhook signature and extract the event.
  // See https://stripe.com/docs/webhooks/signatures for more information.
  try {
    $event = \Stripe\Webhook::constructEvent(
      $payload, $sig_header, getenv('STRIPE_WEBHOOK_SECRET')
    );
  } catch(\UnexpectedValueException $e) {
    // Invalid payload.
    return $response->withStatus(400);
  } catch(\Stripe\Exception\SignatureVerificationException $e) {
    // Invalid Signature.
    return $response->withStatus(400);
  }

  if ($event->type == 'checkout.session.completed') {
    $session = $event->data->object;
    $connectedAccountId = $event->account;
    handleCheckoutSession($connectedAccountId, $session);
  }

  return $response->withStatus(200);
});

function handleCheckoutSession($connectedAccountId, $session) {
  // Fulfill the purchase.
  echo 'Connected account ID: ' . $connectedAccountId;
  echo 'Session: ' . $session;
};

$app->get('/', function (Request $request, Response $response, array $args) {
  return $response->write(file_get_contents(getenv('STATIC_DIR') . '/index.html'));
});

$app->run();
