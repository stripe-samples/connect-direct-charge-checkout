const initializeQuantityControl = () => {
  // The max and min number of items a customer can purchase
  var MIN_ITEMS = 1;
  var MAX_ITEMS = 10;

  document
    .getElementById("quantity-input")
    .addEventListener("change", function (evt) {
      // Ensure customers only buy between 1 and 10 items
      if (evt.target.value < MIN_ITEMS) {
        evt.target.value = MIN_ITEMS;
      }
      if (evt.target.value > MAX_ITEMS) {
        evt.target.value = MAX_ITEMS;
      }
    });

  /* Method for changing the product quantity when a customer clicks the increment / decrement buttons */
  var updateQuantity = function (evt) {
    if (evt && evt.type === "keypress" && evt.keyCode !== 13) {
      return;
    }

    var isAdding = evt && evt.target.id === "add";
    var inputEl = document.getElementById("quantity-input");
    var currentQuantity = parseInt(inputEl.value);

    document.getElementById("add").disabled = false;
    document.getElementById("subtract").disabled = false;

    // Calculate new quantity
    var quantity = evt
      ? isAdding
        ? currentQuantity + 1
        : currentQuantity - 1
      : currentQuantity;
    // Update number input with new value.
    inputEl.value = quantity;
    var amount = config.basePrice / 100;
    var total = (quantity * amount).toFixed(2);

    document
      .getElementById("submit")
      .textContent = `Complete payment $${total}`;

    // Disable the button if the customers hits the max or min
    if (quantity === MIN_ITEMS) {
      document.getElementById("subtract").disabled = true;
    }
    if (quantity === MAX_ITEMS) {
      document.getElementById("add").disabled = true;
    }
  };

  /* Attach method */
  Array.from(document.getElementsByClassName("increment-btn")).forEach(
    (element) => {
      element.addEventListener("click", updateQuantity);
    }
  );

  updateQuantity();
};

const fetchConfig = async () => {
  /* Get the accounts list, publishable key and base price */
  return await fetch("/config")
    .then(function (result) {
      return result.json();
    })
    .then(function (json) {
      // If setupAccounts returns false, there are no accounts that can process a payment, so we
      // won't show the Checkout button.
      if (setupAccounts(json)) {
        window.config = json;
      }
    });
}

/* ------- Account list ------- */

// Fetch 10 most recent accounts from the server. We'll display one of three states in the UI, depending on the
// accounts list; (1) if you haven't created any accounts, we'll re-direct you to the onboarding guide, (2) if none of
// of your accounts have charges enabled, we'll display instructions on how to finish the onboarding process, (3)
// otherwise, we'll display a payment form, as a customer might see it.

// Returns true if there are accounts available to process payments; otherwise returns false.
var setupAccounts = function(data) {
  document.querySelector(".spinner").classList.add("hidden");

  var accounts = data.accounts.data;

  // If there are no accounts, display a message pointing to an onboarding guide.
  if (!accounts.length) {
    document.querySelector("#no-accounts-section").classList.remove("hidden");
    return false;
  }

  var cardPaymentsAccounts = accounts.filter((acct) => !!(acct && acct.capabilities && acct.capabilities.card_payments));
  if (!cardPaymentsAccounts.length) {
    document.querySelector("#no-card-payments-section").classList.remove("hidden");
    return;
  }

  var enabledAccounts = cardPaymentsAccounts.filter((acct) => acct.charges_enabled);

  // If no accounts are enabled, display instructions on how to enable an account. In an actual
  // application, you should only surface Express dashboard links to your connected account owners,
  // not to their customers.
  if (!enabledAccounts.length) {
    var expressAccounts = accounts.filter((acct) => acct.type == 'express');
    var hasCustom = !!accounts.filter((acct) => acct.type == 'custom');
    var hasStandard = !!accounts.filter((acct) => acct.type == 'standard');

    var wrapper = document.querySelector("#disabled-accounts-section");
    var input = document.querySelector("#disabled-accounts-select");
    expressAccounts.forEach((acct) => {
      var element = document.createElement("option");
      element.setAttribute("value", acct.id);
      element.innerHTML = acct.email || acct.id;
      input.appendChild(element)
    });
    // Remove the hidden CSS class on one of the sections with instruction on how to finish onboarding
    // for a given account type.
    if (expressAccounts.length) {
      document.querySelector('#disabled-accounts-form').classList.remove("hidden");
      wrapper.querySelector('.express').classList.remove("hidden");
    }
    else if (hasCustom) {
      wrapper.querySelector('.custom').classList.remove("hidden");
    }
    else if (hasStandard) {
      wrapper.querySelector('.standard').classList.remove("hidden");
    }
    wrapper.classList.remove("hidden");
    return false;
  }

  // If at least one account is enabled, show the account selector and payment form.
  var input = document.querySelector("#enabled-accounts-select");
  enabledAccounts.forEach((acct) => {
    var element = document.createElement("option");
    element.setAttribute("value", acct.id);
    element.innerHTML = (acct.individual && acct.individual.first_name) || acct.email || acct.id;
    input.appendChild(element)
  });

  var wrapper = document.querySelector("#enabled-accounts-section");
  if(wrapper) {
    wrapper.classList.remove("hidden");
  }
  return true;
};

const setupDisabledAccountsForm = () => {
  /* ------- Express dashboard ------- */

  // When no accounts are enabled, this sample provides a way to log in as
  // an Express account to finish the onboarding process. Here, we set up
  // the event handler to construct the Express dashboard link.
  expressDashboardForm = document.querySelector('#disabled-accounts-form');
  expressDashboardForm.addEventListener(
    "submit",
    event => {
      event.preventDefault();
      button = expressDashboardForm.querySelector('button');
      button.setAttribute("disabled", "disabled");
      button.textContent = "Opening...";

      var url = new URL("/express-dashboard-link", document.baseURI);
      params = {account_id: document.querySelector("#disabled-accounts-select").value};
      url.search = new URLSearchParams(params).toString();

      fetch(url, {
        method: "GET",
        headers: {
          "Content-Type": "application/json"
        }
      })
        .then(response => response.json())
        .then(data => {
          if (data.url) {
            window.location = data.url;
          } else {
            elmButton.removeAttribute("disabled");
            elmButton.textContent = "<Something went wrong>";
            console.log("data", data);
          }
        });
    },
    false
  );
}
