using Microsoft.AspNetCore.StaticFiles.Infrastructure;
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
builder.Configuration.AddEnvironmentVariables();

builder.Services.Configure<StripeOptions>(options =>
{
    options.PublicKey = builder.Configuration["STRIPE_PUBLISHABLE_KEY"];
    options.SecretKey = builder.Configuration["STRIPE_SECRET_KEY"];
    options.BasePrice = builder.Configuration["BASE_PRICE"];
    options.WebhookSecret = builder.Configuration["STRIPE_WEBHOOK_SECRET"];
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseDeveloperExceptionPage();
}

var staticFileOptions = new SharedOptions()
{
  FileProvider = new PhysicalFileProvider(
    Path.Combine(Directory.GetCurrentDirectory(), Environment.GetEnvironmentVariable("STATIC_DIR"))
  ),
};

app.UseDefaultFiles(new DefaultFilesOptions(staticFileOptions));
app.UseStaticFiles(new StaticFileOptions(staticFileOptions));

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
    var service = new LoginLinkService();
    var loginLink = await service.CreateAsync(account_id);
    return Results.Redirect(loginLink.Url);
});

app.MapPost("/create-checkout-session", async (IOptions<StripeOptions> options, HttpRequest request) =>
{
    var domainURL = $"{request.Scheme}://{request.Host}";
    var basePrice = long.Parse(options.Value.BasePrice);
    var quantity = long.Parse(request.Form["quantity"]);
    var account = request.Form["account"];

    var sessionOptions = new SessionCreateOptions
    {
        Mode = "payment",
        SuccessUrl = domainURL + "/success.html?session_id={CHECKOUT_SESSION_ID}",
        CancelUrl = domainURL + "/canceled.html",
        LineItems = new()
        {
            new()
            {
                PriceData = new() 
                {
                    UnitAmount = basePrice,
                    Currency = "USD",
                    ProductData = new() 
                    {
                        Name = "Guitar lesson",
                        Images = new List<string> { "https://i.ibb.co/2PNy7yB/guitar.png" },
                    },
                },
                Quantity = quantity,
            }
        },
        PaymentIntentData = new()
        {
            ApplicationFeeAmount = 100
        },
    };

    var checkoutSerice = new SessionService();
    var session = await checkoutSerice.CreateAsync(sessionOptions, new() { StripeAccount = account });
    return Results.Redirect(session.Url);
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
