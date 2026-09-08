export async function onRequest(context) {
  const { request, next } = context;
  const userAgent = (request.headers.get("user-agent") || "").toLowerCase();
  const url = new URL(request.url);

  // If request is made via curl, wget, or bash (or path is /install.sh), serve the shell installer script directly
  if (
    url.pathname === "/install.sh" ||
    url.pathname === "/sh" ||
    userAgent.startsWith("curl/") ||
    userAgent.startsWith("wget/") ||
    userAgent.includes("httpie")
  ) {
    const installScriptUrl = new URL("/install.sh", request.url);
    const assetResponse = await context.env.ASSETS.fetch(installScriptUrl);
    
    return new Response(assetResponse.body, {
      status: 200,
      headers: {
        "content-type": "text/plain; charset=utf-8",
        "cache-control": "no-cache, no-store, must-revalidate",
        "access-control-allow-origin": "*",
      },
    });
  }

  // APK Download tracking & direct redirect route
  if (url.pathname === "/download" || url.pathname === "/apk") {
    // 302 redirect to latest GitHub release APK
    const downloadUrl = "https://github.com/dalnk/shep/releases/download/v0.0.1/shep-v0.0.1.apk";
    return Response.redirect(downloadUrl, 302);
  }

  // Magic Deep Link Activation Gateway (/pair)
  if (url.pathname === "/pair") {
    const pairHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>SHeP // Magic Swarm Activation</title>
  <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🐑</text></svg>">
  <style>
    :root {
      --bg: #000000;
      --card: #0c0d0e;
      --border: #1f2328;
      --text: #f0f6fc;
      --muted: #8b949e;
      --accent: #ff5500;
      --blue: #38bdf8;
      --font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;
      --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: var(--font-sans);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 2rem;
      max-width: 440px;
      width: 100%;
      text-align: center;
    }
    .badge {
      font-family: var(--font-mono);
      font-size: 0.75rem;
      color: var(--accent);
      letter-spacing: 0.1em;
      margin-bottom: 0.75rem;
      display: block;
    }
    h1 { font-size: 1.5rem; margin-bottom: 0.5rem; }
    p { color: var(--muted); font-size: 0.95rem; line-height: 1.5; margin-bottom: 1.5rem; }
    .btn-group { display: flex; flex-direction: column; gap: 0.75rem; }
    .btn {
      display: block;
      padding: 0.85rem 1.25rem;
      border-radius: 4px;
      font-family: var(--font-mono);
      font-size: 0.85rem;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.15s;
    }
    .btn-primary {
      background: var(--text);
      color: #000;
      border: 1px solid var(--text);
    }
    .btn-primary:hover { background: #e1e4e8; }
    .btn-secondary {
      background: #161b22;
      color: var(--blue);
      border: 1px solid var(--border);
    }
    .btn-secondary:hover { background: #21262d; }
  </style>
</head>
<body>
  <div class="card">
    <span class="badge">SHeP // SWARM ACTIVATION</span>
    <h1>Pairing Companion</h1>
    <p>Opening SHeP app to rehydrate swarm credentials and connect your local nodes via passkey.</p>
    <div class="btn-group">
      <a id="openAppBtn" href="#" class="btn btn-primary">OPEN IN SHEP APP</a>
      <a href="/download" class="btn btn-secondary">DOWNLOAD SHEP APK (v0.0.1)</a>
    </div>
  </div>
  <script>
    const hash = window.location.hash || '';
    const search = window.location.search || '';
    const customSchemeUri = 'shep://pair' + search + hash;
    document.getElementById('openAppBtn').href = customSchemeUri;
    // Attempt automatic activation
    setTimeout(() => {
      window.location.href = customSchemeUri;
    }, 400);
  </script>
</body>
</html>`;

    return new Response(pairHtml, {
      status: 200,
      headers: {
        "content-type": "text/html; charset=utf-8",
        "cache-control": "no-cache, no-store, must-revalidate",
      },
    });
  }

  // Otherwise, serve static website
  return next();
}
