# Google Sign-In setup

Proxima uses Google Identity Services to authenticate users. The backend verifies Google's ID token, creates a local user account on first sign-in, and then sets its own signed, HttpOnly `JOB_PORTAL_SESSION` cookie. Passwords are not stored by this application.

## 1. Configure Google Cloud

Create or use a Google OAuth **Web application** client. Add `http://localhost:3000` to its Authorized JavaScript origins for local development; add the deployed frontend URL before production. Copy the Web client ID.

## 2. Configure the backend

Create `.env` beside `pom.xml` from [.env.example](.env.example):

```bash
cp .env.example .env
```

Set these values in `.env`:

```env
JWT_SECRET=paste_the_output_of_openssl_rand_base64_48
GOOGLE_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com
SEED_ADMIN_ENABLED=true
ADMIN_EMAIL=the_google_email_for_your_first_admin@example.com
```

Start the backend. The matching verified Google account is promoted to `SUPER_ADMIN` when it signs in. Set `SEED_ADMIN_ENABLED=false` immediately after that first sign-in; its saved role remains unchanged.

## 3. Configure the frontend

Copy the frontend template and set the same client ID:

```bash
cd ../proxima-frontend
cp .env.example .env.local
```

```env
NEXT_PUBLIC_GOOGLE_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com
```

Restart the Next.js development server after changing `.env.local`.

`JWT_SECRET` must remain private. The Google client ID is public and must **never** be used as the JWT secret.

## GitHub sign-in

Create a GitHub OAuth App for local development with homepage URL
`http://localhost:3000` and callback URL
`http://localhost:8080/api/v1/auth/github/callback`.

Set `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, and `GITHUB_REDIRECT_URI` in
the backend `.env`, then restart the backend. No GitHub client secret belongs
in the frontend. Its login page reads provider availability from the API.
Use a separate production OAuth App with the deployed callback URL.

The initial super-admin bootstrap described above uses a verified Google
identity. A GitHub sign-in alone does not bootstrap an admin account.
