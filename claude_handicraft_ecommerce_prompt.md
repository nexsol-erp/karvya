# Claude Prompt: Production-Ready Handicraft E-Commerce Website

Act as a senior e-commerce solution architect, UI/UX designer, Java/Spring Boot engineer, React/TypeScript engineer, database designer, security engineer, DevOps engineer, and QA engineer.

Your task is to design and build a complete, production-ready, mobile-first e-commerce application for selling handmade natural-fibre and coir handicrafts.

This must be a functional application—not only a landing-page mock-up, design concept, or architecture document. Implement the public storefront, customer accounts, shopping cart, offline checkout, database persistence, admin login, product management, order management, notifications, tests, Docker deployment, and documentation.

## Available product images

The following initial product photographs are provided with this request:

- `1a.png`
- `2a.png`
- `5a.png`

Use these actual photographs for the initial catalogue and throughout the website. Do not replace them with unrelated stock images. Preserve the original files and generate optimized WebP/AVIF versions for web delivery. Use responsive image sizes, descriptive alt text, and lazy loading where appropriate.

If the exact image paths differ, locate the files in the supplied assets or project workspace before implementing the seed data.

## Required technology stack

Use:

- Java 17 or later
- Spring Boot
- Spring Security
- Spring Data JPA
- React
- TypeScript
- Material UI (MUI)
- MariaDB
- REST APIs
- Flyway database migrations
- Docker and Docker Compose
- Nginx to serve the frontend and proxy backend requests
- SMTP for email notifications
- GitHub Actions for build and test automation and a documented deployment workflow

Use a modular, clean architecture with clear separation among domain, application/service, persistence, security, API, and UI concerns. Keep the implementation understandable and maintainable; do not introduce unnecessary infrastructure.

## Working rules

1. Inspect the existing repository before changing anything. If a codebase already exists, extend it safely and preserve working features. If the project is empty, scaffold it using the required stack.
2. First produce a concise implementation blueprint covering architecture, database model, REST APIs, pages/components, security, and delivery phases. Then continue immediately with implementation. Do not wait for approval unless a decision is genuinely blocking or destructive.
3. Build the application in small, verifiable milestones, but continue until the complete phase-one scope is implemented.
4. Use real database-backed behavior. Do not leave core flows as mocked buttons, hard-coded arrays, TODOs, or placeholder API calls.
5. Do not invent business facts, product specifications, customer testimonials, awards, certifications, discounts, sustainability claims, addresses, contact details, or social-media accounts.
6. When business information is missing, use clearly named configurable placeholders and document exactly where the administrator can change them.
7. Never hard-code credentials or secrets. Supply all environment-specific values through environment variables or secure configuration.
8. Validate important business rules on the server. Never trust browser-supplied prices, totals, stock levels, roles, order status, or customer identity.
9. Use appropriate HTTP status codes and a consistent API error format. Do not expose stack traces or sensitive data to clients.
10. Before declaring completion, run the available builds and tests, fix failures caused by the implementation, and report the exact verification results and any genuine remaining limitations.

## Visual and brand direction

The storefront must feel premium, warm, artistic, natural, and professional—not like a generic admin template.

Derive the visual language from the uploaded products. Use a restrained palette based on:

- Coconut brown
- Warm beige and ivory
- Terracotta or deep red
- Muted forest green
- Charcoal for primary text

Use elegant, readable typography, generous whitespace, strong product photography, natural textures used sparingly, subtle shadows, refined rounded cards, and tasteful micro-interactions. Animations must respect `prefers-reduced-motion`.

The public website and admin application must be fully responsive and work well on phones, tablets, laptops, and large screens. Prioritize an excellent mobile shopping experience.

## User roles and access

Support these roles:

- Visitor: browse, search, contact the business, add products to a local cart, and place a guest order.
- Registered customer: all visitor capabilities plus account login, saved profile/addresses, order history, and a server-associated cart after login.
- Administrator: secure access to the admin dashboard and all authorized management functions.

Do not allow public users to self-register as administrators.

## Customer-facing website

### 1. Professional landing page

Create a rich, responsive home page containing:

- Responsive header with configurable logo and store name
- Navigation for Home, Shop, Our Story, Contact, Account, and Cart
- Large hero section using the most suitable uploaded product image
- A strong but factual headline about handcrafted natural décor
- Primary `Shop Collection` call to action
- Secondary `Chat on WhatsApp` call to action
- Featured products
- Product categories
- `Why Choose Handmade?` section
- Craftsmanship or brand-story section using configurable copy
- Natural-material section without unsupported environmental claims
- Contact section
- Professional footer with configurable address, email, WhatsApp, social links, policy links, and copyright

Seed `1a.png`, `2a.png`, and `5a.png` as the first featured products. Because verified product details are not supplied, use clearly editable placeholder names, prices, descriptions, materials, dimensions, and usage notes. Mark placeholder content clearly in seed data or admin documentation.

### 2. Product catalogue

Implement:

- Product categories
- Product search
- Category, price, and availability filters
- Sort by relevance, newest, price low-to-high, and price high-to-low
- Featured products
- In-stock and out-of-stock status
- Product cards showing image, name, formatted price, short description, stock state, and Add to Cart
- Server-side pagination or an accessible load-more flow
- Shareable, clean product URLs based on unique slugs

### 3. Product details

Each product page must include:

- Accessible image gallery
- Product name
- Price
- Full description
- Material
- Colour
- Dimensions
- Care instructions when available
- Stock availability
- Quantity selector with validation
- Add to Cart
- `Ask About This Product on WhatsApp`
- Related products

The WhatsApp button must open:

`https://wa.me/<configured-number>?text=<url-encoded-message>`

The prefilled message must contain the product name, canonical product URL, and a short enquiry. Obtain the WhatsApp number from website settings or an environment variable; never hard-code it.

### 4. Customer registration and account management

Anyone may create a customer account. Implement:

- Customer registration with name, email, mobile number, and password
- Unique normalized email address and, where configured, unique mobile number
- Email/mobile-friendly login identifier design
- Customer login and logout
- Secure password hashing using a modern adaptive algorithm supported by Spring Security
- Change password while logged in, requiring the current password
- Forgot-password and reset-password flow using a single-use, expiring token sent by email
- Profile editing
- Saved delivery addresses
- Customer order history and order-detail view
- Clear account navigation and responsive forms
- Protection against account enumeration in login and password-reset responses
- Rate limiting or throttling for login, registration, and password-reset requests

Email verification may be configurable, but its implementation must not block local development. If SMTP is unavailable, preserve the token/notification attempt securely for development diagnostics without exposing it in production responses.

Customers must never be able to access another customer's profile, cart, address, or order by changing a URL or request identifier.

### 5. Shopping cart

Implement:

- Add to Cart
- Update quantity
- Remove product
- Cart subtotal
- Configurable delivery charge
- Configurable free-delivery threshold
- Order total
- Cart badge in the header
- Friendly empty-cart state
- Continue Shopping and Proceed to Checkout buttons
- Browser-storage cart for visitors
- Database-backed cart for logged-in customers
- Safe guest-cart merge after login, with duplicate products combined without exceeding available stock

Revalidate product existence, active status, price, and stock on the server before presenting the final checkout total and again while creating the order. Never accept the browser's computed totals as authoritative.

### 6. Checkout and offline payment

Online payment is out of scope for phase one. Do not display a fake online-payment form.

Support both guest checkout and registered-customer checkout. Logged-in customers should be able to choose a saved address or enter a new address.

Collect:

- Customer name
- Mobile/WhatsApp number
- Email address, optional for guest checkout unless required for email confirmation
- Address lines
- City
- State
- Postal code
- Delivery notes
- Preferred offline payment method
- Customer comments

Offline payment methods must be configurable by the administrator. Initial examples may include:

- Cash on delivery
- Bank transfer
- UPI after order confirmation
- Pay when collecting the product

Display this message prominently before confirmation:

> Your order will be confirmed by our team. Payment instructions will be shared separately.

When checkout succeeds:

- Create the order and order items transactionally in MariaDB
- Generate a unique, human-readable, non-guessable-enough order number suitable for customer communication
- Revalidate and reserve/decrement stock safely
- Preserve the purchased product name, SKU, unit price, quantity, tax if later configured, and line total as an immutable order-item snapshot
- Record the initial order status and status history
- Link the order to the customer when logged in while retaining the delivery-contact snapshot
- Show a professional confirmation page
- Show items, totals, delivery information, selected payment method, and current payment status
- Clear the corresponding visitor or customer cart only after the order transaction succeeds
- Queue/send an administrator notification email
- Queue/send a customer confirmation email when an email address is available
- Provide a `Send Order Details on WhatsApp` link with the order number and concise summary prefilled

Do not imply that a WhatsApp message was sent automatically. Phase one must use customer-initiated `wa.me` links; automatic outbound messaging requires a separate WhatsApp Business API integration.

Email delivery failure must not roll back or lose an order. Save the order first, record notification attempts, and implement a bounded retry mechanism with status, attempt count, last error, and next-attempt time. Do not continuously poll or lock the database.

### 7. Contact and customer communication

Add a floating WhatsApp button to public pages without obstructing mobile navigation or accessibility controls.

Create a Contact Us page with:

- Name
- Email
- Phone
- Subject
- Message

Validate and save enquiries in the database, notify the configured administrator by email, and show a clear success response even if notification delivery is queued for retry.

Also show configurable clickable email and WhatsApp links. Apply practical spam protection such as rate limiting and a honeypot field; keep CAPTCHA as an optional extension rather than a hard dependency.

## Secure admin application

Create a dedicated admin login and protected admin area.

### Admin security

Implement:

- Spring Security
- Secure password hashing
- Role-based authorization enforced on the server
- Secure HTTP-only authentication cookies with suitable SameSite and Secure settings in production, or an equivalently secure server-managed session design
- CSRF protection for cookie-authenticated state-changing requests
- Login throttling and temporary lockout policy
- Strong input validation
- No hard-coded passwords
- Initial admin bootstrap credentials supplied through environment variables
- Forced password change on first login
- Session invalidation on logout and relevant password/security changes
- Audit fields including created time, updated time, and updated by
- Protection against IDOR, mass assignment, and unrestricted file upload

Restrict product-image uploads by MIME type, extension, file signature, file size, and image dimensions. Generate safe server-side filenames and never execute uploaded content.

### Admin dashboard

Show useful summary cards and lists for:

- New orders
- Orders awaiting confirmation
- Orders awaiting offline payment
- Processing orders
- Shipped orders
- Recent orders
- Total order value for a clearly labelled date range
- Low-stock products
- Recent customer enquiries
- Failed or pending notification attempts

### Product and category management

Allow administrators to:

- Create, edit, activate, deactivate, and archive products
- Upload, replace, reorder, and remove multiple product images
- Set name, slug, SKU, category, description, price, material, colour, dimensions, care instructions, and stock
- Mark products as featured
- Manage categories and category activation
- Preview products before activation
- Prevent accidental permanent deletion of products referenced by orders

Seed the initial catalogue from the three supplied images. Do not rely on seed records after administrators edit them.

### Order and offline-payment management

Allow administrators to:

- View, search, and paginate all orders
- Filter by date, customer, order number, order status, and payment status
- View complete order, item, customer, delivery, status-history, and payment details
- Add internal notes that customers cannot see
- Update order status using validated transitions
- Update payment status
- Record offline payment method, reference, amount, received date, and notes
- Print or download a clean order summary
- Export filtered orders to CSV safely
- Restore reserved stock exactly once when an order is cancelled

Use a controlled order lifecycle such as:

- New
- Confirmed
- Awaiting Payment
- Paid Offline
- Processing
- Shipped
- Delivered
- Cancelled

Model order status and payment status separately. Enforce legal transitions in the service layer, record every transition, and make cancellation/restocking idempotent.

### Customer management

Allow authorized administrators to:

- Search and view registered customers
- View their orders and saved contact information
- Activate or disable customer access without deleting order history
- Initiate a password-reset notification without seeing or setting the customer's existing password

Never expose password hashes, reset tokens, or authentication secrets through APIs or the UI.

### Enquiry management

Allow administrators to:

- View and search contact enquiries
- Mark an enquiry as New, In Progress, or Resolved
- Add internal notes
- Open correctly encoded email or WhatsApp links for the customer

### Website settings

Allow authorized administrators to configure:

- Store name
- Logo
- WhatsApp number
- Administrator notification email
- Business address
- Currency and locale
- Delivery charge
- Free-delivery threshold
- Offline payment methods and customer-facing instructions
- Social-media links
- Homepage headings and introductory copy
- Footer and policy links
- Low-stock threshold

Default to INR and Indian number formatting, but keep currency and locale configurable. Validate setting types and do not allow arbitrary script injection through configurable content.

## Accessibility, SEO, performance, and usability

Implement:

- Semantic HTML
- Keyboard accessibility and visible focus states
- Proper labels and validation messages
- Good colour contrast
- Descriptive image alt text
- Responsive images
- Lazy loading below the fold
- Image compression and sensible caching
- Route-level code splitting where useful
- Loading states and skeletons
- Friendly empty, offline, and error states
- SEO title and description for every public page
- Canonical URLs
- Open Graph metadata
- Product structured data using JSON-LD
- Sitemap
- `robots.txt`
- Clean URLs
- Custom 404 page
- Sensible Core Web Vitals optimizations

Do not index admin, account-security, cart, or checkout pages.

## Backend and database requirements

Design appropriate entities and Flyway migrations for at least:

- Users
- Roles and user-role mapping
- Customer profiles
- Customer addresses
- Password-reset tokens
- Categories
- Products
- Product images
- Customer carts and cart items
- Orders
- Order items
- Order addresses or immutable delivery snapshots
- Order status history
- Offline payments
- Contact enquiries
- Website settings
- Email/notification attempts
- Audit fields and optimistic-lock versions where appropriate

Use database constraints and indexes intentionally. Include unique constraints for identifiers such as normalized email, SKU, slug, order number, and other fields that require uniqueness.

Use transactions for order creation, stock reservation, cancellation, and cart-to-order conversion. Prevent overselling using pessimistic row locking or correctly implemented optimistic locking with retry/error handling. Explain the chosen concurrency strategy briefly in the README.

Do not store full-size product images as database BLOBs. Use a configurable local upload directory behind an image-storage abstraction so object storage can be added later. Persist image metadata and stable relative/object keys in the database.

Provide an interface/extension point for an online payment provider in phase two, but do not implement or expose online payment in phase one.

## REST API expectations

Design versioned APIs, for example under `/api/v1`, covering:

- Public settings needed by the storefront
- Categories and catalogue search
- Product details and related products
- Customer registration, login, logout, current user, password change, password reset, profile, and addresses
- Guest/server cart validation and authenticated customer carts
- Checkout and customer order history
- Contact enquiries
- Admin dashboard summaries
- Admin category, product, image, customer, order, payment, enquiry, settings, and notification management

Use request/response DTOs rather than exposing JPA entities. Add bean validation, pagination metadata, stable sorting, authorization checks, and centralized exception handling. Document the API using OpenAPI/Swagger, keeping production exposure configurable.

## Testing requirements

Include meaningful automated tests for:

- Backend unit tests
- Backend integration tests using a realistic database strategy
- Flyway migration startup
- Frontend components and forms
- API input validation and error responses
- Registration, login, logout, password change, and password reset
- Authentication, authorization, CSRF, and customer data isolation
- Product and image administration
- Visitor cart, login cart merge, and authenticated cart
- Checkout price revalidation
- Successful order creation
- Concurrent stock reservation and overselling prevention
- Order cancellation and idempotent stock restoration
- Email failure not losing an order
- Contact enquiry submission and administration
- At least one end-to-end test covering product browse → cart → checkout → admin order view
- At least one end-to-end test covering registration → login → cart → checkout → customer order history

Tests must assert real outcomes rather than only checking that components render or application contexts start.

## Deployment and operations

Provide:

- Backend Dockerfile
- Frontend Dockerfile
- Nginx configuration for SPA routing, API proxying, compression, caching, and security headers
- `docker-compose.yml`
- MariaDB container with a named persistent volume
- Persistent/configurable product-upload volume
- `.env.example` containing safe placeholders only
- Container health checks
- Separate development and production configuration where needed
- GitHub Actions workflow for backend build/tests, frontend build/tests, and container build validation
- A documented deployment workflow for a Linux cloud server
- HTTPS/reverse-proxy guidance without committing certificates
- MariaDB backup and restoration commands and a basic recovery procedure
- Logging guidance with secret and personal-data redaction
- Application readiness/liveness endpoints with restricted actuator exposure

The application must start locally with documented commands. Avoid using `latest` container tags for production dependencies.

## Required deliverables

Produce and implement all of the following:

1. Concise architecture proposal and key trade-offs
2. Database model and relationship summary
3. REST API inventory
4. Frontend page, route, and component structure
5. Ordered implementation plan
6. Complete backend source code
7. Complete frontend source code
8. Flyway database migrations
9. Initial catalogue seed data using `1a.png`, `2a.png`, and `5a.png`
10. Docker, Docker Compose, and Nginx configuration
11. Automated tests
12. GitHub Actions workflow
13. README containing setup, environment variables, development, test, production deployment, image-storage, SMTP, backup, and restore instructions
14. A final verification report listing commands run, results, remaining configurable placeholders, and any limitations

## Definition of done

Do not claim the project is complete unless all of these work end to end:

- A visitor can browse the seeded products, add items to a cart, contact the business, and place a guest order using an offline payment method.
- A visitor can register, log in, change or reset a password, manage a profile/address, keep a customer cart, place an order, and view only their own order history.
- The server independently validates product prices and stock and prevents overselling.
- An administrator can log in securely, change the initial password, add/edit/archive products, replace product photos, update prices and stock, view enquiries, manage orders, and record offline payments.
- Orders remain saved even if email delivery fails.
- WhatsApp links open with the configured number and correctly encoded contextual messages.
- The application runs through Docker Compose with persistent database and upload storage.
- Builds, migrations, and automated tests pass.
- No secrets are committed, and no unsupported business claims or fabricated data appear in the UI.

Begin by inspecting the repository and supplied images. Present the concise blueprint, then implement the complete application without stopping after the plan.
