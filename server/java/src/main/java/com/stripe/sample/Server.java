package com.stripe.sample;

import java.nio.file.Paths;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.port;
import static spark.Spark.staticFiles;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonSyntaxException;
import com.stripe.Stripe;
import com.stripe.exception.*;

import io.github.cdimascio.dotenv.Dotenv;

import com.stripe.model.Account;
import com.stripe.model.AccountCollection;
import com.stripe.model.LoginLink;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import com.stripe.net.RequestOptions;

public class Server {
    private static Gson gson = new Gson();

    static class PostBody {
        @SerializedName("quantity")
        Long quantity;

        @SerializedName("account")
        String account;

        public Long getQuantity() {
            return quantity;
        }

        public String getAccount() {
            return account;
        }
    }

    private static int computeApplicationFeeAmount(Long basePrice, Long quantity) {
        // Take a 10% cut.
        return (int) (basePrice * quantity * 0.1);
    }

    public static void main(String[] args) {
        port(4242);
        Dotenv dotenv = Dotenv.load();
        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");
        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        get("/", (request, response) -> {
            response.type("application/json");

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("some_key", "some_value");
            return gson.toJson(responseData);
        });

        post("/create-checkout-session", (request, response) -> {
            response.type("application/json");
            PostBody postBody = gson.fromJson(request.body(), PostBody.class);

            // Create new Checkout Session for the order
            // For full details see https://stripe.com/docs/api/checkout/sessions/create
            Map<String, Object> params = new HashMap<String, Object>();

            Long basePrice = new Long(dotenv.get("BASE_PRICE"));
            Long quantity = postBody.getQuantity();
            String domainUrl = dotenv.get("DOMAIN");

            ArrayList<String> paymentMethodTypes = new ArrayList<>();
            paymentMethodTypes.add("card");
            params.put("payment_method_types", paymentMethodTypes);

            ArrayList<String> images = new ArrayList<>();
            images.add("https://i.ibb.co/2PNy7yB/guitar.png");

            ArrayList<HashMap<String, Object>> lineItems = new ArrayList<>();
            HashMap<String, Object> lineItem = new HashMap<String, Object>();
            lineItem.put("name", "Guitar lesson");
            lineItem.put("images", images);
            lineItem.put("amount", basePrice);
            lineItem.put("currency", "USD");
            lineItem.put("quantity", quantity);
            lineItems.add(lineItem);
            params.put("line_items", lineItems);

            HashMap<String, Object> paymentIntentData = new HashMap<String, Object>();
            paymentIntentData.put("application_fee_amount", computeApplicationFeeAmount(basePrice, quantity));
            params.put("payment_intent_data", paymentIntentData);

            // ?session_id={CHECKOUT_SESSION_ID} means the redirect will have the session ID set as a query param
            params.put("success_url", domainUrl + "/success.html?session_id={CHECKOUT_SESSION_ID}");
            params.put("cancel_url", domainUrl + "/canceled.html");

            // Set the account passed from the front-end as the Stripe-Account header
            String account = postBody.getAccount();
            RequestOptions requestOptions = RequestOptions.builder().setStripeAccount(account).build();
            Session session = Session.create(params, requestOptions);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("sessionId", session.getId());
            return gson.toJson(responseData);
        });

        get("/config", (request, response) -> {
            response.type("application/json");

            Map<String, Object> params = new HashMap<>();
            params.put("limit", 10);
            AccountCollection accounts = Account.list(params);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("publicKey", dotenv.get("STRIPE_PUBLISHABLE_KEY"));
            responseData.put("basePrice", dotenv.get("BASE_PRICE"));
            responseData.put("currency", "USD");
            responseData.put("accounts", accounts);
            return gson.toJson(responseData);
        });

        get("/express-dashboard-link", (request, response) -> {
            String accountId = request.queryParams("account_id");
            Map<String, Object> params = new HashMap<>();
            params.put("redirect_url", request.scheme() + "://" + request.host());
            LoginLink link = LoginLink.createOnAccount(accountId, params, null);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("url", link.getUrl());
            return gson.toJson(responseData);
        });

        post("/webhook", (request, response) -> {
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");

            // Uncomment and replace with a real secret. You can find your endpoint's
            // secret in your webhook settings.
            // String webhookSecret = "whsec_..."

            Event event = null;

            // Verify webhook signature and extract the event.
            // See https://stripe.com/docs/webhooks/signatures for more information.
            try {
                event = Webhook.constructEvent(
                    payload, sigHeader, dotenv.get("STRIPE_WEBHOOK_SECRET")
                );
            } catch (JsonSyntaxException e) {
            // Invalid payload.
                response.status(400);
                return "";
            } catch (SignatureVerificationException e) {
            // Invalid Signature.
                response.status(400);
                return "";
            }

            if ("checkout.session.completed".equals(event.getType())) {
                // Deserialize the nested object inside the event
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                Session session = null;
                if (dataObjectDeserializer.getObject().isPresent()) {
                session = (Session) dataObjectDeserializer.getObject().get();
                String connectedAccountId = event.getAccount();
                handleCheckoutSession(connectedAccountId, session);
                } else {
                // Deserialization failed, probably due to an API version mismatch.
                // Refer to the Javadoc documentation on `EventDataObjectDeserializer` for
                // instructions on how to handle this case, or return an error here.
                }
            }

            response.status(200);
            return "";
        });
    }

    private static void handleCheckoutSession(String connectedAccountId, Session session) {
        // Fulfill the purchase.
        System.out.println("Connected account ID: " + connectedAccountId);
        System.out.println("Session ID: " + session.getId());
    }
}