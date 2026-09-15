const http = require("http");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 8082);
// Must match trade-api's local-profile default; override with JWT_SECRET for any shared environment.
const DEFAULT_SECRET = process.env.JWT_SECRET || "local-dev-only-trade-api-jwt-secret-not-for-production-use-000001";

function base64UrlEncodeWordArray(buffer) {
  return Buffer.from(buffer)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function base64UrlEncodeString(str) {
  return base64UrlEncodeWordArray(Buffer.from(str, "utf8"));
}

function parseJsonBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
      if (data.length > 1024 * 1024) {
        reject(new Error("Request body too large"));
      }
    });
    req.on("end", () => {
      if (!data.trim()) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(data));
      } catch (error) {
        reject(new Error("Invalid JSON body"));
      }
    });
    req.on("error", reject);
  });
}

function hmacForAlgorithm(signAlg, signingInput, secret) {
  if (signAlg === "HS256") {
    return crypto.createHmac("sha256", secret).update(signingInput).digest();
  }
  if (signAlg === "HS384") {
    return crypto.createHmac("sha384", secret).update(signingInput).digest();
  }
  if (signAlg === "HS512") {
    return crypto.createHmac("sha512", secret).update(signingInput).digest();
  }
  throw new Error("Unsupported sign alg: " + signAlg);
}

function generateToken(input) {
  const secret = String(input.secret || DEFAULT_SECRET);
  const accountId = Number(input.accountId);
  const expOffsetSec = Number(input.expOffsetSec ?? 600);
  const headerAlg = String(input.headerAlg || "HS256");
  const signAlg = String(input.signAlg || "HS256");

  if (!Number.isFinite(accountId) || accountId < 1) {
    throw new Error("accountId must be a positive number");
  }
  if (!Number.isFinite(expOffsetSec)) {
    throw new Error("expOffsetSec must be a number");
  }

  const now = Math.floor(Date.now() / 1000);
  const exp = now + expOffsetSec;

  const header = { typ: "JWT", alg: headerAlg };
  const payload = { accountId, exp };

  const encodedHeader = base64UrlEncodeString(JSON.stringify(header));
  const encodedPayload = base64UrlEncodeString(JSON.stringify(payload));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  const hmac = hmacForAlgorithm(signAlg, signingInput, secret);
  const signature = base64UrlEncodeWordArray(hmac);
  const token = `${signingInput}.${signature}`;

  return { token, header, payload, now, secretUsed: secret, signAlgUsed: signAlg };
}

function json(res, statusCode, body) {
  res.writeHead(statusCode, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);

    if (req.method === "GET" && url.pathname === "/health") {
      json(res, 200, { status: "ok" });
      return;
    }

    if (url.pathname === "/token" && (req.method === "GET" || req.method === "POST")) {
      const inputFromQuery = {
        secret: url.searchParams.get("secret"),
        accountId: url.searchParams.get("accountId"),
        expOffsetSec: url.searchParams.get("expOffsetSec"),
        headerAlg: url.searchParams.get("headerAlg"),
        signAlg: url.searchParams.get("signAlg")
      };

      const body = req.method === "POST" ? await parseJsonBody(req) : {};
      const input = { ...inputFromQuery, ...body };
      const result = generateToken(input);

      json(res, 200, {
        token: result.token,
        header: result.header,
        payload: result.payload,
        now: result.now,
        signAlgUsed: result.signAlgUsed
      });
      return;
    }

    json(res, 404, {
      error: "Not found",
      routes: [
        "GET /health",
        "GET /token?accountId=1&expOffsetSec=600&secret=...&headerAlg=HS256&signAlg=HS256",
        "POST /token"
      ]
    });
  } catch (error) {
    json(res, 400, { error: error.message || "Bad request" });
  }
});

server.listen(PORT, () => {
  console.log(`JWT token server listening on http://localhost:${PORT}`);
});
