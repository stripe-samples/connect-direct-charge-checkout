package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strconv"

	"github.com/joho/godotenv"
	"github.com/stripe/stripe-go/v72"
	"github.com/stripe/stripe-go/v72/account"
	"github.com/stripe/stripe-go/v72/loginlink"
	"github.com/stripe/stripe-go/v72/checkout/session"
	"github.com/stripe/stripe-go/v72/webhook"
)

func main() {
	err := godotenv.Load()
	if err != nil {
		log.Fatal("Error loading .env file")
	}

	stripe.Key = os.Getenv("STRIPE_SECRET_KEY")

	// For sample support and debugging, not required for production:
	stripe.SetAppInfo(&stripe.AppInfo{
		Name:    "stripe-samples/connect-direct-charge-checkout",
		Version: "0.0.1",
		URL:     "https://github.com/stripe-samples",
	})

	http.Handle("/", http.FileServer(http.Dir(os.Getenv("STATIC_DIR"))))
	http.HandleFunc("/config", handleConfig)
	http.HandleFunc("/create-checkout-session", handleCreateCheckoutSession)
	http.HandleFunc("/express-dashboard-link", handleExpressDashboard)
	http.HandleFunc("/webhook", handleWebhook)

	log.Println("server running at 0.0.0.0:4242")
	http.ListenAndServe("0.0.0.0:4242", nil)
}

// ErrorResponseMessage represents the structure of the error
// object sent in failed responses.
type ErrorResponseMessage struct {
	Message string `json:"message"`
}

// ErrorResponse represents the structure of the error object sent
// in failed responses.
type ErrorResponse struct {
	Error *ErrorResponseMessage `json:"error"`
}

func handleConfig(w http.ResponseWriter, r *http.Request) {
	if r.Method != "GET" {
		http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}
	params := &stripe.AccountListParams{}
	params.Filters.AddFilter("limit", "", "10")
	i := account.List(params)

	writeJSON(w, struct {
		Accounts *stripe.AccountList `json:"accounts"`
		PublicKey string `json:"publicKey"`
		BasePrice string `json:"basePrice"`
	} {
		Accounts: i.AccountList(),
		PublicKey: os.Getenv("STRIPE_PUBLISHABLE_KEY"),
		BasePrice: os.Getenv("BASE_PRICE"),
	})
}

func handleCreateCheckoutSession(w http.ResponseWriter, r *http.Request) {
	r.ParseForm()

	domainURL := os.Getenv("DOMAIN")
	account := r.FormValue("account")

	basePrice, _ := strconv.ParseFloat(os.Getenv("BASE_PRICE"), 32)
	quantity, _ := strconv.ParseFloat(r.FormValue("quantity"), 32)
	fee := int64(basePrice * quantity * 0.10)

	params := &stripe.CheckoutSessionParams{
		SuccessURL:         stripe.String(domainURL + "/success.html?session_id={CHECKOUT_SESSION_ID}"),
		CancelURL:          stripe.String(domainURL + "/canceled.html"),
		Mode:               stripe.String(string(stripe.CheckoutSessionModePayment)),
		LineItems: []*stripe.CheckoutSessionLineItemParams{
			{
				Quantity: stripe.Int64(int64(quantity)),
				PriceData:  &stripe.CheckoutSessionLineItemPriceDataParams{
					UnitAmount: stripe.Int64(int64(basePrice)),
					Currency: stripe.String(string(stripe.CurrencyUSD)),
					ProductData: &stripe.CheckoutSessionLineItemPriceDataProductDataParams{
						Name: stripe.String("Guitar Lesson"),
						Images: stripe.StringSlice([]string{"https://i.ibb.co/2PNy7yB/guitar.png"}),
					},
				},
			},
		},
		PaymentIntentData: &stripe.CheckoutSessionPaymentIntentDataParams{
			ApplicationFeeAmount: stripe.Int64(fee),
		},
	}
	params.SetStripeAccount(account)

	s, err := session.New(params)
	if err != nil {
		http.Error(w, fmt.Sprintf("error while creating session %v", err.Error()), http.StatusInternalServerError)
		return
	}

	http.Redirect(w, r, s.URL, http.StatusSeeOther)
}

func handleExpressDashboard(w http.ResponseWriter, r *http.Request) {
	if r.Method != "GET" {
		http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}

	r.ParseForm()
	account := r.FormValue("account_id")

	params := &stripe.LoginLinkParams{
		Account: stripe.String(account),
	}
	ll, _ := loginlink.New(params)

	http.Redirect(w, r, ll.URL, http.StatusSeeOther)
}

func handleWebhook(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}
	b, err := ioutil.ReadAll(r.Body)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		log.Printf("ioutil.ReadAll: %v", err)
		return
	}

	event, err := webhook.ConstructEvent(b, r.Header.Get("Stripe-Signature"), os.Getenv("STRIPE_WEBHOOK_SECRET"))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		log.Printf("webhook.ConstructEvent: %v", err)
		return
	}

	if event.Type == "checkout.session.completed" {
		var checkoutSession stripe.CheckoutSession
		err := json.Unmarshal(event.Data.Raw, &checkoutSession)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error parsing webhook JSON: %v\n", err)
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		fmt.Println("Checkout Session completed!")
		fmt.Printf("Session: %s\n", checkoutSession.ID)
		fmt.Printf("Account: %s\n", event.Account)
		fmt.Printf("Payment status: %s\n", checkoutSession.PaymentStatus)

		// Note: If you need access to the line items, for instance to
		// automate fullfillment based on the the ID of the Price, you'll
		// need to refetch the Checkout Session here, and expand the line items:
		//
		// params := &stripe.CheckoutSessionParams{}
		// params.AddExpand("line_items")
		// s, _ := session.Get("cs_test_...", params)
		// lineItems := s.LineItems
		//
		// Read more about expand here: https://stripe.com/docs/expand

	}

	if event.Type == "checkout.session.async_payment_succeeded" {
		var checkoutSession stripe.CheckoutSession
		err := json.Unmarshal(event.Data.Raw, &checkoutSession)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error parsing webhook JSON: %v\n", err)
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		fmt.Println("Checkout Session succeeded async!")
		fmt.Printf("Session: %s\n", checkoutSession.ID)
		fmt.Printf("Account: %s\n", event.Account)
		fmt.Printf("Payment status: %s\n", checkoutSession.PaymentStatus)
	}

	writeJSON(w, nil)
}

func writeJSON(w http.ResponseWriter, v interface{}) {
	var buf bytes.Buffer
	if err := json.NewEncoder(&buf).Encode(v); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		log.Printf("json.NewEncoder.Encode: %v", err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	if _, err := io.Copy(w, &buf); err != nil {
		log.Printf("io.Copy: %v", err)
		return
	}
}

func writeJSONError(w http.ResponseWriter, v interface{}, code int) {
	w.WriteHeader(code)
	writeJSON(w, v)
	return
}

func writeJSONErrorMessage(w http.ResponseWriter, message string, code int) {
	resp := &ErrorResponse{
		Error: &ErrorResponseMessage{
			Message: message,
		},
	}
	writeJSONError(w, resp, code)
}
