const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const candidateRoots = [
    __dirname,
    path.join(__dirname, 'pollapp fr'),
    path.resolve(__dirname, '..', 'pollapp fr')
];
const frontendRoot = candidateRoots.find(dir => fs.existsSync(path.join(dir, 'index.html'))) || __dirname;
const port = Number(process.argv[2] || process.env.PORT || 5501);
const backendHost = '127.0.0.1';
const backendPort = 8080;
const contentTypes = {
    '.css': 'text/css; charset=utf-8',
    '.html': 'text/html; charset=utf-8',
    '.ico': 'image/x-icon',
    '.jpeg': 'image/jpeg',
    '.jpg': 'image/jpeg',
    '.js': 'text/javascript; charset=utf-8',
    '.png': 'image/png',
    '.svg': 'image/svg+xml',
    '.webp': 'image/webp'
};

function proxyApi(request, response, requestUrl) {
    const headers = {
        ...request.headers,
        host: `${backendHost}:${backendPort}`
    };
    delete headers.origin;

    const upstream = http.request({
        hostname: backendHost,
        port: backendPort,
        path: `${requestUrl.pathname}${requestUrl.search}`,
        method: request.method,
        headers
    }, (upstreamResponse) => {
        response.writeHead(upstreamResponse.statusCode, upstreamResponse.headers);
        upstreamResponse.pipe(response);
    });

    upstream.on('error', (error) => {
        console.error('Backend proxy request failed:', error.message);
        if (!response.headersSent) {
            response.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
            response.end('Poll API is unavailable. Check that the Spring Boot backend is running on port 8080.');
        } else {
            response.destroy(error);
        }
    });

    request.pipe(upstream);
}

function serveFile(request, response, requestUrl) {
    if (request.method !== 'GET' && request.method !== 'HEAD') {
        response.writeHead(405, { Allow: 'GET, HEAD' });
        response.end();
        return;
    }

    let decodedPath;
    try {
        decodedPath = decodeURIComponent(requestUrl.pathname);
    } catch {
        response.writeHead(400);
        response.end('Invalid URL path.');
        return;
    }

    if (decodedPath === '/' || decodedPath === '') decodedPath = '/index.html';
    if (decodedPath === '/favicon.ico' && !fs.existsSync(path.join(frontendRoot, 'favicon.ico'))) {
        response.writeHead(204);
        response.end();
        return;
    }
    const filePath = path.resolve(frontendRoot, `.${decodedPath}`);
    if (filePath !== frontendRoot && !filePath.startsWith(`${frontendRoot}${path.sep}`)) {
        response.writeHead(403);
        response.end('Forbidden.');
        return;
    }

    fs.stat(filePath, (statError, fileStat) => {
        if (statError || !fileStat.isFile()) {
            response.writeHead(404);
            response.end('Not found.');
            return;
        }

        const contentType = contentTypes[path.extname(filePath).toLowerCase()] || 'application/octet-stream';
        response.writeHead(200, {
            'Content-Length': fileStat.size,
            'Content-Type': contentType,
            'X-Content-Type-Options': 'nosniff'
        });

        if (request.method === 'HEAD') {
            response.end();
            return;
        }

        const fileStream = fs.createReadStream(filePath);
        fileStream.on('error', (error) => {
            console.error('Unable to read frontend file:', error.message);
            if (!response.headersSent) response.writeHead(500);
            response.end('Unable to read file.');
        });
        fileStream.pipe(response);
    });
}

const server = http.createServer((request, response) => {
    let requestUrl;
    try {
        requestUrl = new URL(request.url, 'http://localhost');
    } catch {
        response.writeHead(400);
        response.end('Invalid request URL.');
        return;
    }

    if (requestUrl.pathname === '/polls' || requestUrl.pathname.startsWith('/polls/')) {
        proxyApi(request, response, requestUrl);
        return;
    }

    serveFile(request, response, requestUrl);
});

server.listen(port, () => {
    console.log(`Pollspace frontend and API proxy available at http://127.0.0.1:${port}`);
    console.log(`Frontend files: ${frontendRoot}`);
    console.log(`Poll API proxy: http://${backendHost}:${backendPort}`);
});
