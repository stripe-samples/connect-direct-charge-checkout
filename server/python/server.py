import json
import os
import stripe

from dotenv import load_dotenv, find_dotenv
from flask import Flask, jsonify, render_template, redirect, request, session, send_from_directory, Response

# Setup Stripe python client library
load_dotenv(find_dotenv())
stripe.api_key = os.getenv('STRIPE_SECRET_KEY')
# For sample support and debugging, not required for production:
stripe.set_app_info(
    'stripe-samples/connect-direct-charge-checkout',
    version='0.0.2',
    url='https://github.com/stripe-samples')
stripe.api_version = '2020-08-27'


static_dir = str(os.path.abspath(os.path.join(__file__ , '..', os.getenv('STATIC_DIR'))))
app = Flask(__name__, static_folder=static_dir,
            static_url_path='', template_folder=static_dir)

@app.route('/', methods=['GET'])
def get_example():
    return render_template('index.html')


def compute_application_fee_amount(base_price, quantity):
  # Take a 10% cut.
  return int(0.1 * base_price * quantity)


@app.route('/create-checkout-session', methods=['POST'])
def create_checkout_session():
    domain_url = os.getenv('DOMAIN', default='http://localhost:4242')
    quantity = int(request.form['quantity'])
    base_price = int(os.getenv('BASE_PRICE', default=1000))

    try:
        # Create new Checkout Session for the order
        # For full details see https://stripe.com/docs/api/checkout/sessions/create
        session = stripe.checkout.Session.create(
            mode='payment',
            success_url=domain_url + '/success.html?session_id={CHECKOUT_SESSION_ID}',
            cancel_url=domain_url + '/canceled.html',
            line_items=[{
                'price_data': {
                    'currency': 'USD',
                    'unit_amount': base_price,
                    'product_data': {
                        'name': 'Guitar lesson',
                        'images': ['https://i.ibb.co/2PNy7yB/guitar.png'],
                    },
                },
                'quantity': quantity,
            }],
            payment_intent_data={
                'application_fee_amount': compute_application_fee_amount(base_price, quantity),
            },
            stripe_account=request.form['account']
        )
        return redirect(session.url, code=303)
    except Exception as e:
        return jsonify(error=str(e)), 403


@app.route('/config', methods=['GET'])
def get_config():
    accounts = stripe.Account.list(limit=10)
    return jsonify({
        'accounts': accounts,
        'publicKey': os.getenv('STRIPE_PUBLISHABLE_KEY'),
        'basePrice': os.getenv('BASE_PRICE', default=1000),
    })


@app.route('/express-dashboard-link', methods=['GET'])
def get_express_dashboard_link():
    account_id = request.args.get('account_id')
    link = stripe.Account.create_login_link(account_id, redirect_url=(request.url_root))
    return redirect(link.url, code=303)


@app.route('/webhook', methods=['POST'])
def webhook_received():
  payload = request.get_data()
  signature = request.headers.get('stripe-signature')

  # Verify webhook signature and extract the event.
  # See https://stripe.com/docs/webhooks/signatures for more information.
  try:
    event = stripe.Webhook.construct_event(
        payload=payload, sig_header=signature, secret=os.getenv('STRIPE_WEBHOOK_SECRET')
    )
  except ValueError as e:
    # Invalid payload.
    print(e)
    return Response(status=400)
  except stripe.error.SignatureVerificationError as e:
    # Invalid Signature.
    print(e, signature, os.getenv('STRIPE_WEBHOOK_SECRET'), payload)
    return Response(status=400)

  if event.type == "checkout.session.completed":
    session = event["data"]["object"]
    handle_checkout_session(event.account, session)

  return json.dumps({"success": True}), 200

  if event.type == "checkout.session.completed":
    session = event["data"]["object"]
    connected_account_id = event.account
    handle_checkout_session(connected_account_id, session)

  if event.type == "checkout.session.async_payment_succeeded":
    session = event["data"]["object"]
    connected_account_id = event.account
    handle_checkout_session(connected_account_id, session)

  return json.dumps({"success": True}), 200


def handle_checkout_session(connected_account_id, session):
  # Fulfill the purchase.
  print('Connected account ID: ' + connected_account_id)
  print('Session: ' + str(session))
  print('Sessions payment status: ' + session.payment_status)

if __name__== '__main__':
    app.run(port=4242, debug=True)
