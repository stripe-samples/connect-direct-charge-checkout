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
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.AccountListParams;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import com.stripe.net.RequestOptions;

public class Server {
    private static Gson gson = new Gson();

    private static Long computeApplicationFeeAmount(Long basePrice, Long quantity) {
        // Take a 10% cut.
        return (Long) Double.valueOf(basePrice * quantity * 0.1).longValue();
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
            Long basePrice = new Long(dotenv.get("BASE_PRICE", "1000"));
            Long quantity = new Long(request.queryParams("quantity"));
            String accountId = request.queryParams("account");
            String domainUrl = dotenv.get("DOMAIN", "http://localhost:4242");

            SessionCreateParams params =
                SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(domainUrl + "/success.html?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(domainUrl + "/canceled.html")
                .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                    .setApplicationFeeAmount(computeApplicationFeeAmount(basePrice, quantity))
                    .build()
                )
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(quantity)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("usd")
                            .setUnitAmount(basePrice)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName("Guitar Lesson")
                                .addImage("https://i.ibb.co/2PNy7yB/guitar.png")
                                .build())
                            .build())
                        .build())
                .build();

            // Set the Stripe Account header.
            RequestOptions requestParams = RequestOptions.builder()
                .setStripeAccount(accountId)
                .build();

            try {
                Session session = Session.create(params, requestParams);
                response.redirect(session.getUrl(), 303);
                return "";
            } catch(StripeException ex) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("error", "Request failed");
                return gson.toJson(responseData);
            }
        });

        get("/config", (request, response) -> {
            response.type("application/json");
            AccountListParams params = AccountListParams.builder()
                .setLimit(10L)
                .build();
            AccountCollection accounts = Account.list(params);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("publicKey", dotenv.get("STRIPE_PUBLISHABLE_KEY"));
            responseData.put("basePrice", dotenv.get("BASE_PRICE", "1000"));
            responseData.put("accounts", accounts);
            return gson.toJson(responseData);
        });

        get("/express-dashboard-link", (request, response) -> {
            String accountId = request.queryParams("account_id");
            Map<String, Object> params = new HashMap<>();
            params.put("redirect_url", request.scheme() + "://" + request.host());
            LoginLink link = LoginLink.createOnAccount(accountId, params, null);
            response.redirect(link.getUrl(), 303);
            return "";
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
            if ("checkout.session.async_payment_succeeded".equals(event.getType())) {
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
        System.out.println("Payment status: " + session.getPaymentStatus());
    }
}
