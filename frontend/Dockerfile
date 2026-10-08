# Stage 1: build the static bundle with Bun
FROM oven/bun:1 AS build
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile
COPY . .
# Public, build-time values baked into the bundle. Empty hides Google sign-in, the CAPTCHA widget and the card form.
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_RECAPTCHA_SITE_KEY=
ARG VITE_STRIPE_PUBLISHABLE_KEY=
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID VITE_RECAPTCHA_SITE_KEY=$VITE_RECAPTCHA_SITE_KEY \
    VITE_STRIPE_PUBLISHABLE_KEY=$VITE_STRIPE_PUBLISHABLE_KEY
RUN bun run build

# Stage 2: serve the static bundle with nginx, over HTTPS
FROM nginx:alpine
# openssl makes the self-signed certificate on first start (15-self-signed-cert.sh)
RUN apk add --no-cache openssl
# a template: the image's entrypoint fills in ${HTTPS_PORT} and writes conf.d/default.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --chmod=755 15-self-signed-cert.sh /docker-entrypoint.d/15-self-signed-cert.sh
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80 443
