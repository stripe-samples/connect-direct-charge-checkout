# frozen_string_literal: true

require 'stripe'
require 'sinatra'
require 'dotenv'

# Replace if using a different env file or config
Dotenv.load
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

enable :sessions
set :static, true
set :public_folder, File.join(File.dirname(__FILE__), ENV['STATIC_DIR'])
set :port, 4242

helpers do
  def request_headers
    env.each_with_object({}) { |(k, v), acc| acc[Regexp.last_match(1).downcase] = v if k =~ /^http_(.*)/i; }
  end
end

get '/' do
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

def compute_application_fee_amount(base_price, quantity)
  (0.1 * base_price * quantity).to_i
end

post '/create-checkout-session' do
  content_type 'application/json'
  data = JSON.parse request.body.read

  base_price = ENV['BASE_PRICE'].to_i
  quantity = data['quantity'].to_i

  # Create new Checkout Session for the order
  # For full details see https:#stripe.com/docs/api/checkout/sessions/create
  session = Stripe::Checkout::Session.create({
    # ?session_id={CHECKOUT_SESSION_ID} means the redirect will have the session ID set as a query param
    success_url: ENV['DOMAIN'] + '/success.html?session_id={CHECKOUT_SESSION_ID}',
    cancel_url: ENV['DOMAIN'] + '/canceled.html',
    payment_method_types: ['card'],
    line_items: [{
      name: 'Guitar lesson',
      images: ['https://i.ibb.co/2PNy7yB/guitar.png'],
      quantity: quantity,
      currency: 'USD',
      amount: base_price
    }],
    payment_intent_data: {
      application_fee_amount: compute_application_fee_amount(base_price, quantity),
    },
  }, stripe_account: data['account'])

  {
    sessionId: session['id']
  }.to_json
end

get '/config' do
  content_type 'application/json'
  accounts = Stripe::Account.list({limit: 10})
  {
    accounts: accounts,
    publicKey: ENV['STRIPE_PUBLISHABLE_KEY'],
    basePrice: ENV['BASE_PRICE'],
    currency: ENV['CURRENCY'] 
  }.to_json
end

get '/express-dashboard-link' do
  account_id = params[:account_id]
  link = Stripe::Account.create_login_link(account_id, redirect_url: (request.base_url))
  {'url': link.url}.to_json
end

post '/webhook' do
  payload = request.body.read
  sig_header = request.env['HTTP_STRIPE_SIGNATURE']

  event = nil

  # Verify webhook signature and extract the event.
  # See https://stripe.com/docs/webhooks/signatures for more information.
  begin
    event = Stripe::Webhook.construct_event(
      payload, sig_header, ENV['STRIPE_WEBHOOK_SECRET']
    )
  rescue JSON::ParserError => e
    # Invalid payload.
    puts e
    status 400
    return
  rescue Stripe::SignatureVerificationError => e
    # Invalid Signature.
    puts payload
    puts sig_header
    puts ENV['STRIPE_WEBHOOK_SECRET']
    status 400
    return
  end

  if event['type'] == 'checkout.session.completed'
    session = event['data']['object']
    connected_account_id = event['account']
    handle_checkout_session(connected_account_id, session)
  end

  status 200
end

def handle_checkout_session(connected_account_id, session)
  # Fulfill the purchase.
  puts 'Connected account ID: ' + connected_account_id
  puts 'Session: ' + session.to_s
end