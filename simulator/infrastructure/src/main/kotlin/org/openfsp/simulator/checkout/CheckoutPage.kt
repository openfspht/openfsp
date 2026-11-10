package org.openfsp.simulator.checkout

import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

@Component
class CheckoutPage {
    fun render(
        operator: String,
        payment: SimulatedPayment,
    ): String {
        val amount = HtmlUtils.htmlEscape(payment.amount)
        val action = "/$operator/checkout/${HtmlUtils.htmlEscape(payment.paymentId)}"
        val settled = payment.outcome != null
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>$operator checkout</title>
              <style>
                body { font-family: system-ui, sans-serif; background: #f4f4f5; display: grid; place-items: center; min-height: 100vh; margin: 0 }
                main { background: white; padding: 2rem; width: min(22rem, 90vw); box-shadow: 0 1px 3px rgb(0 0 0 / 0.1) }
                h1 { font-size: 1rem; margin: 0 0 .25rem } p { color: #52525b; margin: 0 0 1.5rem }
                strong { display: block; font-size: 2rem; margin-bottom: 1.5rem }
                button { width: 100%; padding: .75rem; border: 0; margin-top: .5rem; font-size: 1rem; cursor: pointer }
                .pay { background: #0b3b36; color: white } .cancel { background: #e4e4e7 }
              </style>
            </head>
            <body>
              <main>
                <h1>$operator, simulated operator</h1>
                <p>Test environment: no money moves.</p>
                <strong>$amount HTG</strong>
                ${if (settled) "<p>This payment is already settled.</p>" else """
                <form method="post" action="$action"><input type="hidden" name="decision" value="pay"><button class="pay">Pay</button></form>
                <form method="post" action="$action"><input type="hidden" name="decision" value="cancel"><button class="cancel">Cancel</button></form>"""}
              </main>
            </body>
            </html>
        """.trimIndent()
    }
}
