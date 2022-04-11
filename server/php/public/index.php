<?php

require_once 'shared.php';

$accounts = $stripe->accounts->all(['limit' => 10]);

?>
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>Stripe Direct Charge with Checkout</title>
    <meta name="description" content="Stripe Direct Charge with Checkout" />

    <link rel="icon" href="favicon.ico" type="image/x-icon" />
    <link rel="stylesheet" href="css/normalize.css" />
    <link rel="stylesheet" href="css/global.css" />
    <script src="https://js.stripe.com/v3/"></script>
    <script src="/script.js"></script>
  </head>

  <body>
    <div class="sr-root">
      <div class="sr-main">
        <h1>Accept a payment with direct charges and Checkout</h1>
        <!-- Section to display when no connected accounts have been created -->
        <div id="no-accounts-section" class="hidden">
          <div>You need to <a href="https://stripe.com/docs/connect/enable-payment-acceptance-guide#create-account">create an account</a> before you can process a payment.</div>
        </div>
        <!-- Section to display when no connected accounts have been created -->
        <div id="no-card-payments-section" class="hidden">
          <div>None of your recently created accounts have the <a href="https://stripe.com/docs/connect/capabilities-overview#card-payments"><code>card_payments</code> capability</a>. Either request <code>card_payments</code> on an account or consider using <a href="https://github.com/stripe-samples/connect-destination-charge">destination charges</a> instead.</div>
        </div>
        <!-- Section to display when connected accounts have been created, but none have charges enabled -->
        <div id="disabled-accounts-section" class="hidden">
          <div>None of your recently created accounts have charges enabled. <span class="express hidden">Log in to an Express account's dashboard to complete the onboarding process.</span><span class="custom hidden">Manage your Custom accounts and complete the onboarding process <a href="https://dashboard.stripe.com/test/connect/accounts/overview">in the dashboard.</a></span><span class="standard hidden">View your Standard accounts <a href="https://dashboard.stripe.com/test/connect/accounts/overview">in your platform's dashboard</a>, and use their credentials to log in to Stripe and complete the onboarding process.</span></div>
          <form id="disabled-accounts-form" class="hidden">
            <div class="sr-form-row">
              <label for="disabled-accounts-select">Disabled account</label>
              <!-- Options are added to this select in JS -->
              <select id="disabled-accounts-select" class="sr-select">

              </select>
            </div>
            <div class="sr-form-row">
              <button type="submit" class='full-width'>View Express dashboard</button>
            </div>
          </form>
        </div>
        <!-- Section to display when at least one connected account has charges enabled -->
        <?php if(count($accounts) > 0) { ?>
        <div id="enabled-accounts-section">
          <form action="/create-checkout-session.php" method="post">
            <section class="container">
              <div>
                <h1>Guitar lessons</h1>
                <h4>$10/hour</h4>
                <div class="item-image">
                  <img
                    src="guitar.png"
                    width="140"
                    height="160"
                  />
                </div>
              </div>
              <div class="quantity-setter">
                <button type="button" class="increment-btn" id="subtract" disabled>
                  -
                </button>
                <input type="number" id="quantity-input" name="quantity" min="1" value="1" />
                <button type="button"  class="increment-btn" id="add">+</button>
              </div>
              <p class="sr-legal-text">Number of hours (max 10)</p>
              <div class="sr-form-row">
                <label for="enabled-accounts-select">Pick a teacher</label>
                <select id="enabled-accounts-select" class="sr-select" name="account">
                  <?php foreach($accounts as $account){
                     if($account->charges_enabled) { ?>
                    <option value="<?= $account->id ?>"><?= $account->email ?></option>
                  <?php }} ?>
                </select>
              </div>
              <button id="submit"></button>
            </section>
          </form>
          <div id="error-message"></div>
        </div>
        <?php } ?>
      </div>
    </div>
    <script>
      window.config = {
        basePrice: "<?= $_ENV['BASE_PRICE'] ?>",
      }
      initializeQuantityControl();
    </script>
  </body>
</html>
