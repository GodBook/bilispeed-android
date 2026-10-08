package app.bilispeed.browser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/** Uses system TLS validation and checks every redirect before following it. */
final class UpdateClient {
    interface Progress {
        boolean cancelled();
        void received(long bytes, long total);
    }

    private final String repository;
    private volatile HttpsURLConnection active;

    UpdateClient(String repository) { this.repository = repository; }

    ReleaseInfo latest() throws IOException {
        String url = ReleaseInfo.repositoryUrl(repository) + "/releases/latest/download/update.json";
        HttpsURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkInterrupted();
                if (output.size() + count > 32 * 1024) throw new IOException("更新信息过大");
                output.write(buffer, 0, count);
            }
            return ReleaseInfo.parse(output.toString(StandardCharsets.UTF_8.name()), repository);
        } finally {
            connection.disconnect();
            active = null;
        }
    }

    void download(ReleaseInfo release, File partial, Progress progress) throws IOException {
        HttpsURLConnection connection = open(release.apkUrl);
        boolean complete = false;
        try {
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 0 && contentLength != release.size) {
                throw new IOException("更新包大小与发布信息不符");
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                long received = 0;
                while ((count = input.read(buffer)) != -1) {
                    checkInterrupted();
                    if (progress.cancelled()) throw new InterruptedIOException("下载已取消");
                    received += count;
                    if (received > release.size) throw new IOException("更新包超出声明大小");
                    output.write(buffer, 0, count);
                    progress.received(received, release.size);
                }
                if (received != release.size) throw new IOException("更新包下载不完整，请重试");
                output.getFD().sync();
                complete = true;
            }
        } finally {
            connection.disconnect();
            active = null;
            if (!complete) partial.delete();
        }
    }

    void cancel() {
        HttpsURLConnection connection = active;
        if (connection != null) {
            // Disconnect can wait for a TLS/socket lock. Never do that on the activity's UI thread.
            Thread cleanup = new Thread(connection::disconnect, "bilispeed-update-cancel");
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }

    private HttpsURLConnection open(String value) throws IOException {
        URL url = new URL(value);
        for (int redirect = 0; redirect <= 5; redirect++) {
            checkInterrupted();
            if (!allowedNetworkUrl(url.toString(), repository)) {
                throw new IOException("更新请求跳转到了不受信任的地址");
            }
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            active = connection;
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "BiliSpeed/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", "application/octet-stream, application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Cache-Control", "no-cache");
            try {
                int status = connection.getResponseCode();
                if (status == 200) return connection;
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("更新服务器缺少跳转地址");
                    url = new URL(url, location);
                } else if (status == 404) {
                    throw new IOException("更新渠道暂不可用，请稍后重试");
                } else {
                    throw new IOException("更新服务器返回 " + status + "，请稍后重试");
                }
            } catch (IOException exception) {
                connection.disconnect();
                active = null;
                throw exception;
            }
            connection.disconnect();
            active = null;
        }
        throw new IOException("更新服务器跳转次数过多");
    }

    static boolean allowedNetworkUrl(String value, String repository) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getFragment() != null || !uri.normalize().equals(uri)) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("github.com")) {
                String path = uri.getRawPath();
                return path != null && !path.contains("%") && path.startsWith("/" + repository + "/releases/");
            }
            return host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("更新请求已取消");
    }
}
