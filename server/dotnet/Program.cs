using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Options;
using Stripe;
using Stripe.Checkout;

DotNetEnv.Env.Load();
StripeConfiguration.ApiKey = Environment.GetEnvironmentVariable("STRIPE_SECRET_KEY");

StripeConfiguration.AppInfo = new AppInfo
{
    Name = "stripe-samples/connect-direct-charge-checkout",
    Url = "https://github.com/stripe-samples",
    Version = "0.0.1",
};

var builder = WebApplication.CreateBuilder(args);

builder.Services.Configure<StripeOptions>(options =>
{
    options.PublicKey = Environment.GetEnvironmentVariable("STRIPE_PUBLISHABLE_KEY");
    options.SecretKey = Environment.GetEnvironmentVariable("STRIPE_SECRET_KEY");
    options.BasePrice = Environment.GetEnvironmentVariable("BASE_PRICE");
    options.WebhookSecret = Environment.GetEnvironmentVariable("STRIPE_WEBHOOK_SECRET");
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseDeveloperExceptionPage();
}

app.UseDefaultFiles();
app.UseStaticFiles(new StaticFileOptions()
{
    FileProvider = new PhysicalFileProvider(
        Path.Combine(Directory.GetCurrentDirectory(),
        Environment.GetEnvironmentVariable("STATIC_DIR"))
    ),
    RequestPath = new PathString("")
});

app.MapGet("/", () => Results.Redirect("index.html"));

app.MapGet("/config", async (IOptions<StripeOptions> options) =>
{
    var accOptions = new AccountListOptions { Limit = 10 };
    var service = new AccountService();
    StripeList<Account> accounts = await service.ListAsync(accOptions);

    var result = accounts.Select(a => new
    {
        a.Id,
        a.Type,
        a.Email,
        Capabilities = new
        {
            card_payments = a.Capabilities.CardPayments
        },
        Charges_enabled = a.ChargesEnabled
    });

    return new
    {
        Accounts = new { Data = result },
        options.Value.PublicKey,
        options.Value.BasePrice
    };
});

app.MapGet("/express-dashboard-link", async (HttpRequest request, string account_id) =>
{
    var domainURL = $"{request.Scheme}://{request.Host}";
    var options = new AccountLinkCreateOptions
    {
        Account = account_id,
        ReturnUrl = domainURL,
        Type = "account_onboarding",
    };
    var service = new AccountLinkService();
    var accLink = await service.CreateAsync(options);

    return Results.Ok(new { accLink.Url });
});

app.MapPost("/create-checkout-session", async (HttpRequest request) =>
{
    var domainURL = $"{request.Scheme}://{request.Host}";
    var payload = await request.ReadFromJsonAsync<CreateSessionRequest>();
    var basePrice = long.Parse(Environment.GetEnvironmentVariable("BASE_PRICE"));
    var sessionOptions = new SessionCreateOptions
    {
        Mode = "payment",
        PaymentMethodTypes = new List<string> { "card" },
        LineItems = new()
        {
            new()
            {
                Name = "Guitar lesson",
                Images = new List<string> { "https://i.ibb.co/2PNy7yB/guitar.png" },
                Quantity = payload.Quantity,
                Currency = "USD",
                Amount = basePrice,
            }
        },
        PaymentIntentData = new()
        {
            ApplicationFeeAmount = 100
        },
        SuccessUrl = domainURL + "/success.html?session_id={CHECKOUT_SESSION_ID}",
        CancelUrl = domainURL + "/canceled.html"
    };

    var checkoutSerice = new SessionService();
    var session = await checkoutSerice.CreateAsync(sessionOptions, new() { StripeAccount = payload.Account });
    return Results.Ok(new { SessionId = session.Id });
});

app.MapPost("/webhook", async (HttpRequest request, IOptions<StripeOptions> options) =>
{
    var json = await new StreamReader(request.Body).ReadToEndAsync();
    Event stripeEvent;

    try
    {
        stripeEvent = EventUtility.ConstructEvent(json, request.Headers["Stripe-Signature"], options.Value.WebhookSecret);
        app.Logger.LogInformation($"Webhook notification with type: {stripeEvent.Type} found for {stripeEvent.Id}");
    }
    catch (Exception e)
    {
        app.Logger.LogInformation($"Something failed {e}");
        return Results.BadRequest();
    }
    if (stripeEvent.Type == Events.CheckoutSessionCompleted)
    {
        var session = stripeEvent.Data.Object as Stripe.Checkout.Session;
        app.Logger.LogInformation($"Session ID: {session.Id}");
        app.Logger.LogInformation($"Connected Account ID: {stripeEvent.Account}");
    }

    return Results.Ok(new { Received = true });
});

app.Run();

public record CreateSessionRequest(int Quantity, string Account);