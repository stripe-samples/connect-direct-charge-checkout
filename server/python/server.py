#! /usr/bin/env python3.6

'''
server.py
Stripe Sample.
Python 3.6 or newer required.
'''

import json
import os
import random
import string

import stripe
from dotenv import load_dotenv, find_dotenv
from flask import Flask, jsonify, render_template, redirect, request, session, send_from_directory, Response
import urllib

# Setup Stripe python client library
load_dotenv(find_dotenv())
stripe.api_key = os.getenv('STRIPE_SECRET_KEY')
stripe.api_version = os.getenv('STRIPE_API_VERSION', '2019-12-03')

static_dir = str(os.path.abspath(os.path.join(__file__ , '..', os.getenv('STATIC_DIR'))))
app = Flask(__name__, static_folder=static_dir,
            static_url_path='', template_folder=static_dir)

# Set the secret key to some random bytes. Keep this really secret!
# This enables Flask sessions.
app.secret_key = b'_5#y2L"F4Q8z\n\xec]/'

@app.route('/', methods=['GET'])
def get_example():
    return render_template('index.html')


def compute_application_fee_amount(base_price, quantity):
  # Take a 10% cut.
  return int(0.1 * base_price * quantity)


@app.route('/create-checkout-session', methods=['POST'])
def create_checkout_session():
    data = json.loads(request.data)
    domain_url = os.getenv('DOMAIN')
    quantity = int(data['quantity'])
    base_price = int(os.getenv('BASE_PRICE'))

    try:
        # Create new Checkout Session for the order
        # For full details see https://stripe.com/docs/api/checkout/sessions/create
        checkout_session = stripe.checkout.Session.create(
            # ?session_id={CHECKOUT_SESSION_ID} means the redirect will have the session ID set as a query param
            success_url=domain_url + '/success.html?session_id={CHECKOUT_SESSION_ID}',
            cancel_url=domain_url + '/canceled.html',
            payment_method_types=['card'],
            line_items=[
                {
                    'name': 'Guitar lesson',
                    'images': ['https://i.ibb.co/2PNy7yB/guitar.png'],
                    'quantity': quantity,
                    'currency': 'USD',
                    'amount': base_price
                }
            ],
            payment_intent_data={
                'application_fee_amount': compute_application_fee_amount(base_price, quantity),
            },
            stripe_account=data['account']
        )
        return jsonify({'sessionId': checkout_session['id']})
    except Exception as e:
        return jsonify(error=str(e)), 403


@app.route('/config', methods=['GET'])
def get_config():
    accounts = stripe.Account.list(limit=10)
    return jsonify({
        'accounts': accounts,
        'publicKey': os.getenv('STRIPE_PUBLISHABLE_KEY'),
        'basePrice': os.getenv('BASE_PRICE'),
        'currency': os.getenv('CURRENCY') 
    })


@app.route('/express-dashboard-link', methods=['GET'])
def get_express_dashboard_link():
    account_id = request.args.get('account_id')
    link = stripe.Account.create_login_link(account_id, redirect_url=(request.url_root))
    return jsonify({'url': link.url})


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

  if event["type"] == "checkout.session.completed":
    session = event["data"]["object"]
    handle_checkout_session(session)

  return json.dumps({"success": True}), 200

  if event["type"] == "checkout.session.completed":
    session = event["data"]["object"]
    connected_account_id = event["account"]
    handle_checkout_session(connected_account_id, session)

  return json.dumps({"success": True}), 200


def handle_checkout_session(connected_account_id, session):
  # Fulfill the purchase.
  print('Connected account ID: ' + connected_account_id)
  print('Session: ' + str(session))


if __name__== '__main__':
    app.run(port=4242)
