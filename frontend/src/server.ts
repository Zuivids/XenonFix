import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';
import { oldAddressAnswer } from './server/old-addresses';
import { SitemapProducts, sitemapXml } from './server/sitemap';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
// Caddy in front sets these. The host is still checked against allowedHosts.
const angularApp = new AngularNodeAppEngine({
  trustProxyHeaders: ['x-forwarded-for', 'x-forwarded-host', 'x-forwarded-proto'],
});
const sitemapProducts = new SitemapProducts(process.env['BACKEND_URL'] || 'http://localhost:8090');

/** Old site addresses, before anything else, so a stale link never shows the wrong page. */
app.use((req, res, next) => {
  const answer = oldAddressAnswer(req.originalUrl);
  if (!answer) {
    next();
  } else if (answer.status === 301) {
    res.redirect(301, answer.location);
  } else {
    res.status(410).type('text/plain').send('Gone');
  }
});

app.get('/sitemap.xml', async (_req, res) => {
  try {
    const xml = sitemapXml(await sitemapProducts.get());
    res.type('application/xml').set('Cache-Control', 'public, max-age=600').send(xml);
  } catch (error) {
    console.error('Sitemap: the backend did not answer', error);
    res.status(503).set('Retry-After', '600').type('text/plain').send('Try again later');
  }
});

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
