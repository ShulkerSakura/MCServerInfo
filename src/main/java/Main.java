import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import low.citory.MinecraftPinger;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class Main {
    public static final String VERSION = "1.3";
    public static final String AUTHOR = "NyaShulker 2531493755@qq.com";

    static Locale getLocale() {
        if (Locale.getDefault().equals(Locale.CHINA)) {
            return Locale.CHINA;
        } else {
            return Locale.ENGLISH;
        }
    }

    public static ResourceBundle i18n = ResourceBundle.getBundle(
            "i18n", getLocale(), new UTF8ResourceBundleControl()
    );

    static class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    /**
     * 解析服务器地址字符串，支持：
     * - localhost
     * - localhost:25565
     * - [::1]
     * - [2001:db8::1]:30000
     * @param address 地址字符串
     * @return 解析后的 host 和 port
     * @throws IllegalArgumentException 格式错误
     */
    /**
     * 解析服务器地址，支持：
     * - host
     * - host:port
     * - [IPv6]
     * - [IPv6]:port
     * - 域名无端口时自动查询 SRV 记录（_minecraft._tcp.host）
     */
    public static HostPort parseHostPort(String address) throws IllegalArgumentException {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException(i18n.getString("app.error.serverEmpty"));
        }

        String host;
        int port = -1; // -1 表示尚未确定端口

        // === 1. IPv6: [开头
        if (address.startsWith("[")) {
            int bracketEnd = address.indexOf(']');
            if (bracketEnd == -1) {
                throw new IllegalArgumentException(i18n.getString("ipv6.syntax"));
            }

            host = address.substring(1, bracketEnd);

            // 检查是否有 :port
            if (bracketEnd + 1 < address.length() && address.charAt(bracketEnd + 1) == ':') {
                String portStr = address.substring(bracketEnd + 2);
                try {
                    port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException(i18n.getString("port.inRange"));
                    }
                    return new HostPort(host, port); // ✅ 有端口，直接返回
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(i18n.getString("port.invalid") + portStr);
                }
            }
            // IPv6 无端口 → 进入 SRV 或默认流程
        }
        // === 2. 普通 host 或 host:port
        else {
            int lastColon = address.lastIndexOf(':');
            // 避免把 IPv4 的 : 当成端口分隔符（如 192.168.1.1:25565 是合法的）
            if (lastColon > 0 && lastColon > address.lastIndexOf('.') + 1) {
                String maybePort = address.substring(lastColon + 1);
                if (maybePort.matches("\\d+")) {
                    try {
                        port = Integer.parseInt(maybePort);
                        if (port < 1 || port > 65535) {
                            throw new IllegalArgumentException(i18n.getString("port.inRange"));
                        }
                        host = address.substring(0, lastColon);
                        return new HostPort(host, port); // ✅ 有端口，直接返回
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(i18n.getString("port.invalid"));
                    }
                }
            }
            // 无端口或非法端口 → 使用 host 全部
            host = address;
        }

        // === 3. 到这里说明：没有提供端口，尝试 SRV 查询
        if (port == -1) {
            HostPort srvResult = lookupMinecraftSRV(host);
            if (srvResult != null) {
                return srvResult; // ✅ 使用 SRV 的 host 和 port
            }
        }

        // === 4. 最终 fallback：默认端口 25565
        return new HostPort(host, 25565);
    }

    private static final String[] SRV_PATTERNS = {
            "_minecraft._tcp.%s",  // 标准 Minecraft SRV
            "_mc._tcp.%s",         // 常见别名
            "_game._tcp.%s"        // 可选扩展
    };

    /**
     * 查询 Minecraft SRV 记录
     * @param domain 域名
     * @return 成功则返回 HostPort，否则 null
     */
    static HostPort lookupMinecraftSRV(String domain) {
        // 本地地址不查 SRV
        if (domain.equals("localhost") ||
                domain.equals("127.0.0.1") ||
                domain.equals("::1")) {
            return null;
        }

        // 检查是否是 IP 地址（IPv4 或 IPv6）
        if (isValidIP(domain)) {
            return null;
        }

        for (String pattern : SRV_PATTERNS) {
            String srvName = String.format(pattern, domain);
            try {
                Lookup lookup = new Lookup(srvName, Type.SRV);
                lookup.setResolver(new SimpleResolver());
                lookup.setCache(null);

                Record[] records = lookup.run();
                if (records != null && records.length > 0) {
                    SRVRecord srv = (SRVRecord) records[0];
                    String target = srv.getTarget().toString().replaceAll("\\.$", "");
                    int port = srv.getPort();
                    return new HostPort(target, port);
                }
            } catch (Exception e) {
                // 忽略，尝试下一个
            }
        }

        return null;
    }

    /**
     * 检查是否为 IP 地址（避免对 IP 查 SRV）
     */
    static boolean isValidIP(String host) {
        return host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") ||  // IPv4
                host.matches("([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}") ||  // IPv6 简单匹配
                host.equals("localhost");
    }

    public static void main(String[] args) throws IOException {
        String mode = null;
        int index = 0;
        GUI gui = new GUI();

        // 如果没有参数，直接进入GUI
        if (args.length == 0) {
            if (GraphicsEnvironment.isHeadless()) {
                printUsage();
            } else {
                GUI.startGui(); // 无参数 → 启动 GUI
            }
            return;
        }
        String arg = args[index];
        switch (arg) {
            case "-h":
            case "--help":
                printUsage();
                return;
            case "-v":
            case "--version":
                System.out.println(i18n.getString("app.version") + VERSION + " " + i18n.getString("app.author") + AUTHOR);
                return;
            case "-c":
            case "--cli":
                if (mode != null) {
                    System.err.println(i18n.getString("app.error.onlyOneMode"));
                    printUsage();
                    return;
                }
                mode = "cli";
                index++;

                // 检查是否有二级参数 "api"
                boolean useJson = false;
                if (index < args.length && "api".equalsIgnoreCase(args[index])) {
                    useJson = true;
                    index++;
                }

                // 必须还有一个参数：服务器地址（如 localhost:25565）
                if (index >= args.length) {
                    System.err.println(i18n.getString("app.error.needServerAddress"));
                    printUsage();
                    return;
                }

                String address = args[index++];
                HostPort hp;
                try {
                    hp = parseHostPort(address);
                } catch (IllegalArgumentException e) {
                    System.err.println(i18n.getString("app.error.addressAnalyzeFailed") + e.getMessage());
                    return;
                }

                runAsCli(hp.host, hp.port, useJson);
                return;
            case "-s":
            case "--server":
                if (mode != null) {
                    System.err.println(i18n.getString("app.error.onlyOneMode"));
                    printUsage();
                    return;
                }
                mode = "server";
                index++;

                if (index >= args.length) {
                    System.err.println(i18n.getString("app.error.needListenPort"));
                    printUsage();
                    return;
                }

                int listenPort;
                try {
                    listenPort = Integer.parseInt(args[index++]);
                } catch (NumberFormatException e) {
                    System.err.println(i18n.getString("app.error.listenPortMustBeNumber"));
                    return;
                }

                runAsServer(listenPort);
                System.out.println(i18n.getString("app.quitServer"));
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    System.out.println(i18n.getString("app.serverStopped"));
                }
                return; // Server 模式执行完退出

            default:
                System.err.println(i18n.getString("app.unknownParameter") + arg);
                printUsage();
        }
    }

    static void printUsage(){
        System.out.println(i18n.getString("app.author") + AUTHOR);
        System.out.println(i18n.getString("app.usage"));
        System.out.println(i18n.getString("usage.version"));
        System.out.println(i18n.getString("usage.versionCmd"));
        System.out.println(i18n.getString("usage.cli"));
        System.out.println(i18n.getString("usage.cliCmd"));
        System.out.println(i18n.getString("usage.server"));
        System.out.println(i18n.getString("usage.serverCmd"));
    }

    /**
     * 简单判断字符串是否为合法 JSON（仅用于判断是否可以作为对象插入）
     * 注意：非常简化的实现，仅检查是否以 { 开头并能匹配括号
     */
    private static boolean isValidJson(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        str = str.trim();

        // 必须以 { 或 [ 开头
        if (!str.startsWith("{") && !str.startsWith("[")) {
            return false;
        }

        // 简单括号匹配
        int balance = 0;
        boolean inString = false;
        char quote = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true;
                    quote = c;
                } else if (c == quote) {
                    if (i == 0 || str.charAt(i - 1) != '\\') {
                        inString = false;
                    }
                }
            }

            if (!inString) {
                if (c == '{' || c == '[') balance++;
                if (c == '}' || c == ']') balance--;
            }
        }

        return balance == 0;
    }

    static String runAsCli(String serverAddress, int serverPort, boolean useJson) {
        MinecraftPinger pinger = new MinecraftPinger(serverAddress, serverPort);
        String result;
        if (!pinger.isOnline()) {
            if (useJson) {
                String jsonResponse = String.format(
                        "{"
                                + "\"host\":\"%s\","
                                + "\"port\":%d,"
                                + "\"online\":false,"
                                + "\"error\":\"Server is offline or unreachable\""
                                + "}",
                        serverAddress, serverPort
                );
                System.out.println(jsonResponse);
                result = jsonResponse;
            } else {
                result = i18n.getString("log.server") + serverAddress + ":" + serverPort + i18n.getString("log.offline");
                System.out.println(result);
            }
            return result;
        }

        if (useJson) {
            // 准备 motd 字段：尝试保留为 JSON 对象，否则作为字符串
            String motdJson;
            String rawMotd = pinger.getRawMotd();
            if (isValidJson(rawMotd)) {
                motdJson = rawMotd;  // ✅ 是合法 JSON，直接使用（不加引号，不转义）
            } else {
                motdJson = "\"" + escapeJson(rawMotd) + "\"";  // ❌ 不是 JSON，作为字符串处理，需加引号并转义
            }

            // 使用 %s 插入 motd（注意：如果它是对象，就不带外层引号）
            String jsonResponse = String.format(
                    "{"
                            + "\"host\":\"%s\","
                            + "\"port\":%d,"
                            + "\"online\":true,"
                            + "\"version\":\"%s\","
                            + "\"protocol\":%d,"
                            + "\"playersOnline\":%d,"
                            + "\"maxPlayers\":%d,"
                            + "\"ping\":%d,"
                            + "\"motd\":%s"  // ← 注意：这里用 %s，但 motdJson 自己决定是否带引号
                            + "}",
                    serverAddress,
                    serverPort,
                    escapeJson(pinger.getVersion()),
                    pinger.getProtocolVersion(),
                    pinger.getPlayersOnline(),
                    pinger.getMaxPlayers(),
                    pinger.getServerPing(),
                    motdJson  // ✅ 根据情况传入：{"text":"..."} 或 "\"纯文本\""
            );

            System.out.println(jsonResponse);
            return jsonResponse;
        } else {
            // 原始格式输出
            result = i18n.getString("result.version") + pinger.getVersion() + "\n"
                    + i18n.getString("result.protocol") + pinger.getProtocolVersion() + "\n"
                    + i18n.getString("result.players") + pinger.getPlayersOnline() + "/" + pinger.getMaxPlayers() + "\n"
                    + i18n.getString("result.ping") + pinger.getServerPing() + "ms" + "\n"
                    + i18n.getString("result.motd") + pinger.getAnsiMotd() + "\n";
            System.out.printf(result);
        }
        return result;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    // 简化响应发送方法
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void runAsServer(int listenPort) throws IOException {
        System.out.println(i18n.getString("app.server.startListenOn") + listenPort);
        HttpServer server = HttpServer.create(new InetSocketAddress(listenPort), 0);
        server.createContext("/api", exchange -> {
            try {
                // 只处理 GET 请求
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                    return;
                }

                // 获取 ? 后面的服务器地址（如：localhost:25565）
                String query = exchange.getRequestURI().getRawQuery();
                if (query == null || query.trim().isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing server address\"}");
                    return;
                }

                // 解码（支持中文等）
                String address = URLDecoder.decode(query, StandardCharsets.UTF_8);
                HostPort hp;
                try {
                    hp = parseHostPort(address);
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                    return;
                }

                MinecraftPinger pinger = new MinecraftPinger(hp.host, hp.port);
                String jsonResponse;
                int statusCode;
                try {
                    if (pinger.isOnline()) {
                        // 成功：返回服务器信息
                        jsonResponse = String.format(
                                "{"
                                        + "\"host\":\"%s\","
                                        + "\"port\":%d,"
                                        + "\"online\":true,"
                                        + "\"version\":\"%s\","
                                        + "\"protocol\":%d,"
                                        + "\"playersOnline\":%d,"
                                        + "\"maxPlayers\":%d,"
                                        + "\"ping\":%d,"
                                        + "\"motd\":\"%s\""
                                        + "}",
                                hp.host,
                                hp.port,
                                escapeJson(pinger.getVersion()),
                                pinger.getProtocolVersion(),
                                pinger.getPlayersOnline(),
                                pinger.getMaxPlayers(),
                                pinger.getServerPing(),
                                escapeJson(pinger.getRawMotd())
                        );
                        statusCode = 200;
                    } else {
                        // 离线
                        jsonResponse = String.format(
                                "{"
                                        + "\"host\":\"%s\","
                                        + "\"port\":%d,"
                                        + "\"online\":false,"
                                        + "\"error\":\"Server is offline or unreachable\""
                                        + "}",
                                hp.host, hp.port
                        );
                        statusCode = 500;
                    }
                } catch (Exception e) {
                    // 异常（如超时、连接失败）
                    jsonResponse = String.format(
                            "{"
                                    + "\"host\":\"%s\","
                                    + "\"port\":%d,"
                                    + "\"online\":false,"
                                    + "\"error\":\"Connection failed: %s\""
                                    + "}",
                            hp.host, hp.port, escapeJson(e.getMessage())
                    );
                    statusCode = 500;
                }

                // 返回响应
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                sendResponse(exchange, statusCode, jsonResponse);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println(i18n.getString("app.server.started") + "http://localhost:" + listenPort + "/api?youraddress" + i18n.getString("app.server.or") + "http://localhost:" + listenPort + "/api?youraddress:yourport");
    }
}