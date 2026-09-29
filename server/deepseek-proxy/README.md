# DeepSeek proxy

Keeps the DeepSeek key off phones and enforces each install's monthly allowance (6M tokens by
default). Runs free on Cloudflare Workers.

## Deploy (about 10 minutes, once)

```sh
cd server/deepseek-proxy
npx wrangler login
npx wrangler kv namespace create USAGE        # paste the id into wrangler.toml
npx wrangler secret put DEEPSEEK_API_KEY       # paste the key when asked; it never goes in git
npx wrangler deploy                            # prints https://meetingmind-deepseek.<you>.workers.dev
```

## Build the app against it

```sh
DEEPSEEK_PROXY_URL=https://meetingmind-deepseek.<you>.workers.dev ./gradlew assembleDebug
```

The APK then has DeepSeek built in with no key inside it. Change the monthly allowance with
`MONTHLY_TOKENS` in `wrangler.toml`.
