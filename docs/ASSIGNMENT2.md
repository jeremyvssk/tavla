    Project 2 (Commerce) - Shopping experience. Let users fill their carts, guide them through checkout, handle payments safely, and manage their orders from start to finish.
    Project 3 (Experience) - Complete user interface and management tools. Build all customer-facing pages, create admin dashboards for managing the business, and add the security and performance features needed for real-world use.

Shopping Cart

Ever abandoned a cart because it was too much hassle?
Well designed shopping cart is where you encourage window shoppers to become paying customers. Transparent costs, easy to modify, you know the drill.
Smooth cart experience is like a good waiter - there when you need it, invisible when you don't, and never makes you do math in your head.

    Display essential product information for each item, including name, price, and a thumbnail image.
    Enable users to add and remove items, update quantities, and view real-time total calculations.
    Develop a guest cart for non-registered users, ensuring their selections are saved temporarily.
    Create a persistent cart that retains items across sessions for logged-in users.
    Incorporate a section for related or recommended products based on the items in the cart.

Checkout Process

You've got them to the cart, now push it over the finish line.

    Implement a single-page checkout that collects basic information, address input and payment selection (prefill known information for logged in users).
    Include shipping options and validate the address for accuracy.
    Provide order summary with an option to update quantities or remove items.
    After completing the checkout, display an order confirmation page with a summary, and send an email confirmation.

Payments

Time to talk money - specifically, how you're going to handle it. This isn't the area where you want to cut corners or get creative.
PCI compliance isn't just a bunch of fancy letters. In the real world, a slip-up in payment handling procedures may cost millions (Equifax), tank the reputation (Target) or lose the right to handle payments alltogether (Heartland Payment Systems).

    Simulate payments using Stripe/PayPal sandboxes, handling payment validation, order processing, and payment failure scenarios.
    Use secure payment forms that adhere to best practices — no sensitive payment information (e.g., card details) should be stored on your server.
    Implement front-end validation for card details.
    Manage payment statuses (Pending/Success/Failure) linked to the order state:
        Set the order status to "Pending Payment" upon order placement and payment initiation.
        Update the order to "Payment Successful" or "Payment Failed" after processing.
        Publish the payment status to a message queue.
        The Order Service consumes the message to update the order based on the payment status.
        Notify the customer via email of the order status and adjust inventory accordingly.

Consider common payment failures and edge cases such as insufficient funds, incorrect payment details, or gateway errors.
Theoretical concepts

    PCI Compliance - familiarize yourself with PCI DSS requirements for securely handling card information.
    SSL/TLS - understand how SSL/TLS encrypts data between client and server.
    Refunds and Cancellations - learn the processes for managing refunds and cancellations.

Order Management

Sale's done but your job isn't. Order management should give your customers the complete overview of their purchases, statuses and tracking.
As a side perk, it will save your inbox from the flood of "Where's my order?" emails.

    Allow users to filter orders by date and status.
    Provide a detailed order view with status updates and tracking.
    Allow cancellations before the order is processed and set up a refund workflow.
    Use message queues (e.g., RabbitMQ, Apache Kafka) for order processing and updates.

  +--------------------+    Payment Request    +----------------------+
  |                    |  -------------------> |                      |
  |    Order Service   |                       |    Payment Service   |
  |    (Place Order)   |                       |   (Process Payment)  |
  |                    |                       |                      |
  +--------------------+                       +----------------------+
            |                                           |
            v                                           |
  +--------------------+        Payment Status          |
  |                    |  <-----------------------------+
  |   Message Queue    |                                |
  |  (RabbitMQ/Kafka)  |                                |
  +--------------------+                                |
            |                                           v
            v                                  +----------------------+
  +--------------------+                       |                      |
  |                    |     Update Order      |     Order Service    |
  |   Order Service    | --------------------> | (Update Order State) |
  | (Consume Message)  |                       |                      |
  +--------------------+                       +----------------------+

Testing
Automated Tests

    Unit Tests
        Cart Functionality - ensure accurate item management and total calculations.
        Order Summary Calculations - confirm precise pricing and shipping calculations.

    Critical User Flow Tests
        Registration - verify smooth user registration process and data storage.
        Checkout - test complete checkout flow, including guest and logged-in user scenarios.

Manual Tests

    Data Encryption - confirm proper encryption of sensitive data in transit and at rest.

Important Considerations ❗

    Cart Persistence Strategy - balance performance vs. data consistency for guest/user carts
    Payment Security - never store sensitive payment data; use tokenization
    Transaction Integrity - implement proper rollback mechanisms for failed payments
    Message Queue Reliability - handle message failures and implement dead letter queues
    Inventory Management - prevent overselling through proper stock validation

Extra requirements 📚
Dockerization

    Containerize the project: use Docker to simplify setup and execution:
        Provide a Dockerfile (or multiple, if the project includes separate frontend and backend components)
        Include a simple startup command or script that builds and runs the entire application with one step
        Docker and payment simulation CLI are the only prerequisites for running and reviewing this project, with all application dependencies included in the Docker setup

Deliverables and Review Requirements 📁

    All source code and configuration files
    A README file with:
        Project overview
        Entity Relationship Diagram
        Setup and installation instructions
        Usage guide
        Any additional features or bonus functionality implemented

During the review, be prepared to:

    Demonstrate your platforms's functionality
    Explain your code and design choices
    Discuss any challenges you faced and how you overcame them


TESTS:



The README file contains updated project overview, entity relationship diagram, setup instructions, and usage guide for the commerce functionality

The database schema includes tables and relationships to support the shopping cart functionality, including guest carts and persistent carts for logged-in users.

The shopping cart displays product information for each item, including name, price, and a thumbnail image.

Users can add, remove, and update quantities of items in the cart with real-time total calculations.

A guest cart is implemented for non-registered users, saving their selections temporarily.

A persistent cart is implemented for logged-in users, retaining items across sessions.

The system handles out-of-stock scenarios gracefully when users attempt to add items to the cart.

The system implements a single-page checkout process.

The checkout page collects basic information, address input, and payment selection.

For logged-in users, known information is pre-filled in the checkout form.

The system validates entered shipping address for accuracy.

An order summary is provided during checkout, displaying all items, quantities, and costs.

The system sends an email confirmation to the user after a successful order placement.

The checkout process handles and displays appropriate error messages for invalid inputs or failed transactions.

Verify specific error messages for: missing required fields, invalid formats (email, phone, address), payment validation, and network errors.

The payment system integrates with Stripe, PayPal or other similar simulation sandbox APIs.

The payment form uses the payment provider's secure form elements instead of handling card details directly.

The card validation system checks number format, expiry date, and CVV before form submission.

Student can explain the concept of PCI DSS compliance and why sensitive payment data should not be stored on application servers.

The order system updates status appropriately upon receiving callbacks from payment provider (successful or failed payments).

The payment system publishes status updates to a message queue.

The notification system sends appropriate emails for both successful and failed payment scenarios.

The payment system responds to specific failure scenarios.

System must handle: insufficient funds error, invalid card number error, expired card error, and payment gateway timeout

The inventory system prevents overselling during concurrent payments.

Multiple simultaneous payments for the same product should not result in overselling inventory

The order filtering system allows users to sort by date and order status.

The order details page displays full order information including status updates.

The order cancellation system allows cancellations for unprocessed orders.

The inventory system updates stock levels when orders are placed or cancelled.

All sensitive data stored in database is encrypted at rest for order and payment data.

Check encryption implementation for: order details, shipping addresses, and payment transaction records

Student can explain their approach to testing cart functionality, checkout flows, and payment integration.

Automated tests exist for Unit tests (cart functionality, order calculations) and Critical User Flow tests (registration, checkout process).

Ask the student to explain and demonstrate the functionality of the tests.
Extra

Shopping cart implementation quality, user experience, and data persistence across guest/logged-in scenarios.

Checkout process flow, error handling, and user guidance throughout the payment journey.

Payment integration security, transaction handling, and proper response to various payment scenarios.

Order management functionality, status tracking, and message queue implementation.

Project application is containerized using Docker.

The project uses Docker to containerize the application and its dependencies. Host prerequisites are limited to Docker and payment simulation CLI - all other dependencies are managed within containers.
