<?php

require_once 'shared.php';

$domain_url = $_ENV['DOMAIN'];
$quantity = $_POST['quantity'];
$account = $_POST['account'];
$base_price = $_ENV['BASE_PRICE'];
$fee_amount = (int) ($base_price * $quantity * 0.10);

$session = $stripe->checkout->sessions->create([
  'success_url' => $domain_url . '/success.php?session_id={CHECKOUT_SESSION_ID}',
  'cancel_url' => $domain_url . '/canceled.php',
  'mode' => 'payment',
  'line_items' => [[
    'price_data' => [
      'unit_amount' => $base_price,
      'currency' => 'usd',
      'product_data' => [
        'name' => 'Guitar Lesson',
        'images' => ['https://i.ibb.co/2PNy7yB/guitar.png'],
      ]
    ],
    'quantity' => $quantity,
  ]],
  'payment_intent_data' => [
    'application_fee_amount' => $fee_amount,
  ]
], [
  'stripe_account' => $account,
]);

header("HTTP/1.1 303 See Other");
header("Location: " . $session->url);
