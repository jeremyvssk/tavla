# Search Flow — Homework Questions

Come back when you're ready and answer these. Tell Claude to check your answers.

## Table of Contents

1. [Section 1: Frontend (React Layer)](#section-1-frontend-react-layer)
2. [Section 2: Backend (Spring Boot Layer)](#section-2-backend-spring-boot-layer)
3. [Section 3: Database (PostgreSQL Layer)](#section-3-database-postgresql-layer)
4. [Section 4: DTOs and Data Flow](#section-4-dtos-and-data-flow)
5. [Section 5: Full Flow (Tie It All Together)](#section-5-full-flow-tie-it-all-together)

---

## Section 1: Frontend (React Layer)

**Q1.** Why do we debounce the search input instead of sending a request on every keystroke? What would happen if we didn't?

**Q2.** React Query checks something before making a network request. What does it check, and what happens if it finds what it's looking for?

**Q3.** The user searches "matcha", gets results, clears the search bar, then types "matcha" again. Does React Query make a second API call? Why or why not?

**Q4.** Where is the access token stored in our project, and why don't we use localStorage?

**Q5.** The user's access token expired 2 minutes ago but they're still browsing. They hit search. What happens step by step before the search results come back?

---

## Section 2: Backend (Spring Boot Layer)

**Q6.** Name the 3 things the JwtAuthenticationFilter checks before letting a request through. One of them involves Redis — which one and why?

**Q7.** What is a JTI and why can't we just "delete" a JWT to revoke it?

**Q8.** A user logs out, but their access token doesn't expire for another 10 minutes. How does our system handle this? What happens to the Redis entry after 10 minutes?

**Q9.** What is CORS and why would our search request fail without it? What two "origins" are involved in our project?

---

## Section 3: Database (PostgreSQL Layer)

**Q10.** Explain what a tsvector is. Given the text `"Fresh Organic Matcha Green Tea"`, describe what PostgreSQL does to it when storing it as a tsvector (name at least 2 transformations).

**Q11.** What is the difference between `search_vector` and `tsvector`?

**Q12.** What does the `@@` operator do? What types does it compare?

**Q13.** Without a GIN index, how would PostgreSQL find products matching "matcha"? With a GIN index, how does it find them? Why is this faster?

**Q14.** A user types "mactha" (typo). Full-text search (tsvector) returns 0 results. What feature handles this, and how does it decide that "mactha" is close to "matcha"?

**Q15.** What is SQL aggregation? Write (or describe) a query that answers: "How many matcha products are in each category?"

---

## Section 4: DTOs and Data Flow

**Q16.** Why don't we send the Product entity directly as the API response? Give at least 2 reasons.

**Q17.** Name a field that exists on the Product entity but should NOT appear in a search result DTO. Why?

**Q18.** For incoming data (like a registration form), what role does the DTO play before the data reaches the entity?

---

## Section 5: Full Flow (Tie It All Together)

**Q19.** Trace the full path of a search for "matcha" with a category filter of "Tea". Start from the user's keystroke and end at the rendered results on screen. Name every layer and what each one does in 1 sentence.

**Q20.** The user clicks "Snacks (8)" in the facet panel. What changes in the frontend, and what new request gets sent? Does the full flow repeat?
