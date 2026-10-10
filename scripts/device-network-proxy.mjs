// Temporary, localhost-only HTTPS tunnel for USB device tests without phone networking.
// TLS terminates at the official server. No account data or request bodies are logged.
import http from 'node:http';
import net from 'node:net';

const allowed = ['bilibili.com', 'hdslb.com', 'biliimg.com', 'biliapi.net', 'biliapi.com', 'bilivideo.com', 'bilivideo.cn'];
const server = http.createServer((_request, response) => { response.writeHead(403); response.end(); });
server.on('connect', (request, client, head) => {
    const destination = new URL('https://' + request.url);
    if (!allowed.some(domain => destination.hostname === domain || destination.hostname.endsWith('.' + domain))
            || destination.port && destination.port !== '443') { client.end('HTTP/1.1 403 Forbidden\r\n\r\n'); return; }
    const upstream = net.connect(443, destination.hostname, () => {
        client.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        if (head.length) upstream.write(head);
        upstream.pipe(client); client.pipe(upstream);
    });
    upstream.on('error', () => client.destroy());
    client.on('error', () => upstream.destroy());
    client.on('close', () => upstream.destroy());
});
server.listen(8877, '127.0.0.1', () => console.log('USB Bilibili HTTPS tunnel ready on localhost:8877'));
