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

  // Otherwise, serve static website
  return next();
}
