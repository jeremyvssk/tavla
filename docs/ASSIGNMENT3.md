    Project 3 (Experience) - Complete user interface and management tools. Build all customer-facing pages, create admin dashboards for managing the business, and add the security and performance features needed for real-world use.

User Reviews and Ratings

Have you bought something because a stranger on the internet said it was great? That's the power of user reviews. They're not just feedback — they're a trust-building tool and a sales driver rolled into one.
Set up a solid review system, and watch your products sell themselves.

    Implement star ratings system and allow users to leave text reviews.
    Display the average rating for each product based on user feedback.
    Include a "helpful" voting feature for reviews to allow users to rate the usefulness of other reviews.

Admin Functionality

This is your command center where you manage products, monitor orders, and keep your finger on the pulse of your business.
It's great when everything works as designed, but when it doesn't and you need to pop the hood and get your hands dirty, it's even better having all the tools in the right place and knowing which levers to pull.

    Allow admins to add, edit, delete, update and manage products, categories, orders, and refunds.
    Provide the ability to manually update shipping statuses and manage delivery options.
    Enable viewing and managing user accounts, including roles and permissions (e.g., admin, support, sales).
    Support bulk uploads of product data (JSON/CSV).
    Include review moderation.

Ensure these features are intuitive yet secure, with proper role-based access controls in place.
All admins must have 2FA enabled.
UI

A user interface is like a joke. If you have to explain it, it's not that good.

First impressions matter. Your UI is the face of your e-commerce platform - it's what users see, click, and interact with.
You're not just building pages; you're crafting a shopping experience. From the homepage to the checkout, every element should guide users smoothly through their journey.
Keep it clean, keep it responsive, and for crying out loud, make sure it looks good on mobile.
Page 	Description
Home Page 	Displays featured products and categories/collections.
Product/Category Listing Page (PLP) 	Shows product images, titles, ratings; include faceted search, filters, grid/list view toggle, and pagination.
Product Detail Page (PDP) 	Displays product images, detailed information, reviews and ratings, call-to-action (CTA), and related or recommended products.
Shopping Cart Page 	Lists items with thumbnails, prices, quantities; allow updates/removals, displays total (excluding shipping), and provides a clear CTA.
Checkout Page 	Includes shipping and billing addresses, shipping method, payment method, order summary, and support guest checkout/sign-in.
Order Confirmation Page 	Shows order summary, estimated delivery, order ID, and reference number.
User Account Page 	Allows users to manage profile information, view order history, and manage addresses.
Search Results Page 	Shows search results, with filtering and sorting options, along with result count and pagination.
Admin Page 	Provides CRUD functionality for products, order management, user management, review moderation, and bulk uploads.
Contact/Support Page 	Simple (or not, up to you) contact form for users to reach out.
About Page 	Presents company information, mission, team, and links to social media.
Error Page (404) 	Displays a friendly error message when a page isn't found.

    Optionally:

Page 	Description
Terms and Conditions Page 	Includes a privacy policy regarding data collection and usage, and terms of service.
FAQ Page 	Lists common issues (shipping, payment, returns) with clear explanations.

    Additional UI Features (available on every page):
        Quick Shopping Cart Preview - pop-up/drop-down preview of cart contents.
        Quick Search with Suggestions - dynamic search suggestions as users type.

Good UI isn't about fancy animations or cutting-edge design (though those can be nice). It's about making shopping so easy and enjoyable that customers don't even have to think about it.
Security

Security is tricky. The better it gets, the cleverer the bad actors become at exploiting vulnerabilities.
Lock it down like Svalbard Global Seed Vault and you'll frustrate your customers. Cut corners and you'll see your data on sale on the dark web.
It's a constant game of cat and mouse. You're not just setting up a firewall and calling it a day. It's about staying on your toes, keeping up with the latest threats, and patching holes before they become craters.

    Create a Self-Signed TLS Certificate for secure connections.
    Implement Data Encryption for all sensitive data, both in transit and at rest. This includes user credentials, personally identifiable information (PII), order details, session cookies etc.
    Add a Token Bucket rate limiting
    Input validation:
        Client and Server side
        Syntactic Validation (format of inputs for emails, payment information, addresses and similar)
        Semantic Validation (contextually appropriate data)
        Whitelisting

Ultimately, the platforms security comes down to CIA triad principles: Confidentiality, Integrity, and Availability, ensuring that customer data is protected from unauthorized access, remains accurate and reliable, and is accessible to legitimate users.
SEO

It wouldn't be an e-commerce platform if it didn't have Search Engine Optimization. Biggest players in the business have dedicated SEO teams who continuously work on boosting visibility and performance in search engines. This involves analyzing complex data, developing algorithms, implementing AI and ML, etc.
However, you don't have to have a PhD to give your platform a boost in the search engines.

    Title tags - keep under 60 characters, include primary unique keywords
    Meta descriptions - 155-160 characters, include natural keywords, write compelling summary
    H1 tags - one per page, similar to title tag
    H2-H6 tags - structure content hierarchically, include relevant keywords
    Ensure all tags are unique and descriptive for each page

Just by implementing above practices, your platform will be more search-engine-friendly and have a better chance in ranking well in search results.
Accessibility

Your platform needs to ensure inclusivity and accessibility, and take into account users with diverse abilities and needs.
Web Content Accessibility Guidelines (WCAG) are technical standards that help make the digital world accessible to people with disabilities.

To follow WCAG 2.1 Level A success criteria, the platform must have the following:

    Semantic HTML - appropriate HTML5 elements, logically structured headings and proper list and table elements.
    Keyboard navigation - all interactive elements must be focusable with logical tab order.
    ARIA implementation - to be used only if native HTML elements are not enough.
    Color contrast - 4:5:1 ratio for normal text, 3:1 for larger text.
    Image Alt text - descriptive alt attributes to meaningful images
    Responsive text - relative units for font sizes, text and layout remains readable when at zoomed at 200%

Testing
Automated Tests

    Unit Tests
        Product Data Model - verify correct data structure and validation.
        Cart Functionality - ensure accurate item management and total calculations.
        Order Summary Calculations - confirm precise pricing and shipping calculations.
        JWT Token Handling - test token generation, validation, and expiration.
        User Input Validation - verify proper handling of various input scenarios.

    API Integration Tests
        API Endpoints - validate correct responses and error handling for all endpoints.
        Database Operations - ensure proper data persistence and retrieval.

    Critical User Flow Tests
        Product Search - confirm accurate and efficient product search functionality.

    Security Tests
        Rate Limiting - verify effectiveness of rate limiting mechanisms to prevent abuse.

Load Testing

This is a great way to evaluate your platforms performance, and what should be taken into account in further development. Regular load testing will help you establish required optimizations for high traffic periods (promotions, holiday seasons) and bring out any bottlenecks.

You are free to choose your preferred open source Load Testing Tool. When creating test scenarios (at least 3), try to mimic actual user behavavior on the platfrom, for example:

    Browsing product catalogs
    Searching for products
    Adding items to cart
    Completing the checkout process
    User registration and login

Some objectives you should aim for:

    90% of users requests are processed withing 2 seconds.
    Platform supports at least 50 concurrent users without noticeable performance degredation.
    Throughput of at least 10 transactions per second during peak load conditions.
    98% of transactions complete successfully during high traffic.
    Error rate of less than 5% during load tests.

By the end of the project, you should've identified and ready to present:

    Maximum number of concurrent users before response times exceed 5 seconds.
    Expected throughput (transaction per second)
    Normal operating conditions, expected peak loads.
    The load that makes your CPU and/or memory go over 90%.
    Performance bottlenecks.

Important Considerations ❗

    Admin Security - implement principle of least privilege and audit logging
    Content Delivery - consider CDN strategy for images and static assets
    Search Optimization - balance search accuracy with response time

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
        Performance analysis report
        Setup and installation instructions
        Usage guide
        Any additional features or bonus functionality implemented

During the review, be prepared to:

    Demonstrate your platforms's functionality
    Explain your code and design choices
    Discuss any challenges you faced and how you overcame them


TESTING:



The README file contains complete project overview, entity relationship diagram, performance analysis report, setup instructions, and usage guide

Star rating system is implemented with the average star rating calculated from all reviews.

The review system allows users to submit text reviews for purchased products.

The review sorting system orders reviews by helpfulness votes.

The system enforces 2FA for all admin accounts.

The product management system allows CRUD operations for products with all required fields.

Admins can add, edit, delete, update and manage products, caregories, orders and refunds

Admins can manage delivery options and order status updates.

Admins can view all users and assign roles.

Platform supports bulk upload products via JSON/CSV files.

Home page showcases featured products and collections.

Product listing page includes product details, ratings, filters, grid/list views, search, and sorting options.

Product detail page shows complete product information, images, reviews, CTA and related/recommended products.

Shopping cart page lists items with thumbnails, prices, quantities, allows updates/removals, displays total (excluding shipping), and provides clear CTA.

Checkout page contains guest/signed-in user order summary, address input, shipping, and payment selection.

Order confirmation page displays order summary estimated delivery and reference number.

Search results page includes filtering, sorting, result count and pagination.

Admin page provides CRUD functionality for products, order management, user management, review moderation, and bulk uploads.

Contact/Support page includes functioning contact form.

About page includes companies information, mission, team and links to social media.

Error page (404) includes catch-all error message.

Quick cart preview (pop-up/drop-down or similar) is implemented.

Quick search with dynamic suggestions is implemented.

Product images are stored and served in multiple sizes to support different views (e.g., thumbnails, full-size images).

Platform maintains functionality across various (320px, 768px, 1024px, 1440px) viewports.

Self signed TLS certificate is configured.

All sensitive data stored in database is encrypted at rest.

Check encryption implementation for: user credentials, personally identifiable information, shipping addresses, order details and session tokens

Token bucket rate limiting is implemented.

Student can explain CIA (Confidentiality, Integrity, Availability) principles.

The platform implements basic SEO best practices including title tags under 60 characters, proper heading hierarchy (H2-H6), logical URL structure, and descriptive alt text for images.

All meaningful images include descriptive alt text.

Text remains readable when zoomed to 200%.

Student can explain the importance of semantic HTML for accessibility.

Student can explain their approach to testing, integration of automated and usage of manual tests throughout the development process.

Automated tests exist for Unit, API integration, User flow and Security tests.

Ask the student to explain and demonstrate the functionality of the tests.

Load test report identifies maximum concurrent users before response times exceed 5 seconds.

Load test report shows transaction throughput.

Student has identified potential bottlenecks and can propose solutions.
Extra

Platform's UI/UX design quality, consistency, and responsiveness across all pages.

Admin interface functionality, data presentation, and workflow efficiency.

Error handling and user guidance throughout all platform interactions and edge cases.

Implementation of additional features that enhance shopping experience beyond basic requirements.

Security implementation quality, performance optimization, and accessibility compliance.

Project application is containerized using Docker.

The project uses Docker to containerize the application and its dependencies. Host prerequisites are limited to Docker and payment simulation CLI - all other dependencies are managed within containers.
