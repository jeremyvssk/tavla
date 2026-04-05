i-love-shopping (1/3)
The situation 👀

"Give me your credit card number, expiry date and that little code on the back and I'll send you this thing you see on the picture" - In the early 90s that sounded like a plot of a bad movie. Fast forward to today and we're talking about $6.88 trillion industry, projected to reach $8.03 trillion by 2027.
E-commerce is reshaping entire economies and creating job markets that didn't exist a decade ago. From Pizza Hut's first online order back in 1994 to today's one-click purchases and next day deliveries, we've come a long way.

Online retail seems to be booming and Jeff managed to make a few bucks out of it, so why don't you give it a go?
Functional requirements 📋

You'll have to build a Business-to-Consumer (B2C) E-commerce Platform. You're free to choose a category of featured products as long as they meet the criteria outlined below.
Some examples to get your started:

    Electronics and Technology
    Pet Supplies
    Home and Furniture
    Books and Media

The build of the e-commerce platform is broken down into 3 interconnected projects:

    Project 1 (Foundation) - Core system that powers everything. Secure user accounts, a well-structured database, and a product catalog that customers can easily search and browse.
    Project 2 (Commerce) - Shopping experience. Let users fill their carts, guide them through checkout, handle payments safely, and manage their orders from start to finish.
    Project 3 (Experience) - Complete user interface and management tools. Build all customer-facing pages, create admin dashboards for managing the business, and add the security and performance features needed for real-world use.

Rome wasn't built in a day. Have the whole picture in mind, but focus on placing each stone with precision.
User Registration, Authentication and Authorization

User management is the backbone of your platform's security and user experience. There's a good chance that everyone has abandoned a site because of an uncomfortable registration procedure or poorly implemented security measures. Think about times when you've felt uneasy about how a site handles your personal information - that's what we're trying to avoid here.
You want tight security, but not so tight that users feel like they're cracking a safe just to buy socks. And take care not to end up on the list of "oopsies" (Aadhaar, Yahoo, LinkedIn)

    Registration and login should be handled through email-password and OAuth (e.g., Google, Facebook).
    Add CAPTCHA (e.g., Google reCAPTCHA) during registration.
    JWT with access and refresh tokens.
        Access token is used for authenticating API requests, Refresh token is used to obtain new access token when it has expired.
        Short lived access tokens (15-60 minutes), longer-lived refresh tokens (3-7 days).
        Access tokens must be stored in memory, not in local or session storage.
        Refresh token rotation with each token refresh (single use validation).
        Token revocation mechanism for both.
    Include password recovery and reset via email.
    Implement an optional, user enabled two-factor authentication (e.g., Google Authenticator, Authy).
    Validate user inputs and show helpful error messages when things go wrong.

Database

When choosing which Database to go with, consider the following points:

    Data Structure and Complexity: e-commerce typically involves structured data and relational databases are better suited for handling complex relationships and transactions.
    Your project is already big, and it will grow bigger - plan for it.
    Expect your platform to get rapid, heavy traffic during peak promotions and holiday shopping: evaluate read-write operations and features like caching to optimize for performance.

Above all, your database must follow ACID properties:

    Atomicity - critical processes involving multiple steps must all succeed or fail together.
    Consistency - maintain data integrity after transactions, bringing database from one valid state to another.
    Isolation - concurrent executions leave the database in the same state as if they were executed sequentially.
    Durability - if transaction is commited, it will remain commited even in case of system failure.

To better visualize the big picture and identify requirements, design an Entity Relationship Diagram (ERD). The key components of ERD typically include:

    Entities
    Attributes
    Relationships
    Primary Keys
    Foreign Keys
    Cardinality
    Modality

Product Catalog

A solid product catalog is like a well-organized shop where everything's easy to find and looks great on the shelf. What's under the hood? Data structure that would make Marie Kondo proud.
A good catalog doesn't just list products; it guides your customers on a shopping journey. So when you're setting this up, think like a shopper. What would make you click 'Add to Cart' instead of 'Back to Google'?

    Product should have at least the following data models:
    id, name, description, price, stock quantity, category, brand, images, weight/dimensions (metric and imperial)
    Organize products into categories and make them easy to browse.
    Implement faceted search, allowing users to refine results by multiple attributes (e.g., price range, brand, ratings, etc).
    Display dynamic search suggestions as users type, based on their input.
    Offer sorting options like relevance, price, and ratings to better help users find what they want.

Testing

A thousand tests today save a million headaches tomorrow.

Invest your time in creating a comprehensive testing suite that will help you catch bugs early and follow test-driven development practices.
Below are outlined minimum required tests but you're welcome to be as thorough as you'd like.
Automated Tests

While you're not required to implement CI/CD pipeline (yet), these tests should be automated and run frequently, ideally before any bugs make their way to your master code repository.

    Unit Tests
        JWT Token Handling - test token generation, validation, and expiration.
        User Input Validation - verify proper handling of various input scenarios.
        Product Data Model - verify correct data structure and validation.

    API Integration Tests
        API Endpoints - validate correct responses and error handling for all endpoints.
        Database Operations - ensure proper data persistence and retrieval.

    Security Tests
        Input Validation - test protection against injection attacks and malformed inputs.

Test hard and test smart. Use up-to-date, reputable testing frameworks and libraries to enhance the quality and efficiency.
Manual Tests

Automated tests are great but special care must be taken to ensure that security-critical parts of your platform are protected from vulnerabilities seeking bots. Following tests should be run periodically to ensure they serve their purpose as intended.

    CAPTCHA Verification - ensure proper integration and user experience.
    OAuth Integration - verify seamless third-party authentication flows.
    Two-Factor Authentication (2FA) - test setup process and login flow with 2FA enabled.

Important Considerations ❗

    Scalable Architecture - design the system with scalability in mind from the start. Choose an architectural approach that fits your project goals - whether that's a traditional monolith, modular monolith, or microservices architecture.
    Robust Security Measures - implement comprehensive security practices throughout the development process.
    Performance Optimization - focus on optimizing performance from the beginning. This includes efficient database queries, caching strategies, and front-end optimizations.
    Flexible Product Management - design a product catalog system that can easily accommodate various product types, attributes, and categories.
    API-First Approach - develop with an API-first mindset. Well-designed, documented, and versioned APIs will facilitate easier integration with future services, mobile applications, or third-party systems.
    Compliance and Regulatory Awareness - stay informed about e-commerce regulations and data protection laws, like GDPR.

Useful links 🔗

    The Twelve-Factor App
    E-commerce Design
    Docker

Dockerization

    Containerize the project: use Docker to simplify setup and execution:
        Provide a Dockerfile (or multiple, if the project includes separate frontend and backend components)
        Include a simple startup command or script that builds and runs the entire application with one step
        Docker is the only prerequisites for running and reviewing this project, with all application dependencies included in the Docker setup

What you'll learn 🧠

    Develop a full-scale B2C e-commerce platform, covering all essential components from user management to order processing
    Design and build a scalable database structure with ACID properties, suitable for handling complex e-commerce data relationships
    Implement industry-standard security practices and data protection measures for a robust and trustworthy online retail environment
    Create a responsive and accessible user interface that adheres to modern UI/UX principles and WCAG 2.1 Level A criteria
    Apply best practices in software testing, including automated and manual testing strategies, to ensure platform reliability and performance

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


Testing

Ensures that software works as expected by validating features against requirements. It helps catch bugs early, improves reliability, and maintains high-quality standards in development.

How to do testing?

    1. Clone the repository, then build and run the submitted code.
    2. Agree on your teamwork: how do you divide testing between reviewers?
    3. Test functionality and check compliance with the requirements.
    4. Provide feedback in the group chat and request fixes if necessary.
    5. Clearly state what changes are mandatory and what are optional fixes.
    6. Repeat the testing cycle after submitters make the requested changes for as many times as is needed.

Mandatory

The README file contains a clear project overview, entity relationship diagram, setup instructions, and usage guide

The platform implements a Business-to-Consumer (B2C) e-commerce model.

The system implements both email-password and OAuth authentication methods.

CAPTCHA is integrated into the registration process.

Student can explain the concept of JWT and its components (header, payload, signature).

Access tokens are stored in memory.

Refresh token rotation is implemented with single-use validation.

Verify that each refresh token can only be used once and new refresh token is issued with each refresh. Old refresh tokens must be rejected.

Token revocation mechanism is in place for both access and refresh tokens.

Password recovery and reset functionality via email is implemented.

Two-factor authentication (2FA) is available as an optional, user-enabled feature.

User input validation is implemented on both client and server sides for authentication forms.

Student can explain the chosen database's scalability features and how they support potential growth of the e-commerce platform.

Student can explain ACID properties and their importance in e-commerce database design.

An Entity Relationship Diagram (ERD) is provided, clearly showing entities, attributes, relationships, primary keys, foreign keys, cardinality, and modality.

Student can demonstrate and explain the search implementation including database design and basic text search functionality.

The product data model includes all required fields: id, name, description, price, stock quantity, category, brand, images, and weight/dimensions (in both metric and imperial units).

Products are organized into categories with an intuitive browsing structure.

Faceted search is implemented, allowing users to refine results by product attributes (e.g., price range, brand, category).

Product listing includes sorting options for relevance, price and rating.

Product images are stored with proper file handling and basic serving functionality.

Student can explain their approach to testing, integration of automated and usage of manual tests throughout the development process.

Automated tests exist for Unit, API integration, and Security tests covering authentication and product catalog functionality.

Ask the student to explain and demonstrate the functionality of the tests.

Student can explain their chosen architectural approach and justify how it aligns with their platform's scalability requirements.

Authentication system implementation quality, security measures, and user experience.

Database design quality, ERD completeness, and adherence to ACID properties.

Product catalog organization, search functionality, and filtering system implementation.

Project application is containerized using Docker.

The project uses Docker to containerize the application and its dependencies. Docker is the only host prerequisite - all other dependencies are managed within containers.
