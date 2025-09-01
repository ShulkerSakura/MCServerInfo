import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import low.citory.MinecraftPinger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class Main {
    public static final String VERSION = "1.1";
    public static final String AUTHOR = "NyaShulker 2531493755@qq.com";
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
            throw new IllegalArgumentException("服务器地址不能为空");
        }

        String host;
        int port = -1; // -1 表示尚未确定端口

        // === 1. IPv6: [开头
        if (address.startsWith("[")) {
            int bracketEnd = address.indexOf(']');
            if (bracketEnd == -1) {
                throw new IllegalArgumentException("IPv6 地址缺少 ]");
            }

            host = address.substring(1, bracketEnd);

            // 检查是否有 :port
            if (bracketEnd + 1 < address.length() && address.charAt(bracketEnd + 1) == ':') {
                String portStr = address.substring(bracketEnd + 2);
                try {
                    port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException("端口号必须在 1-65535 之间");
                    }
                    return new HostPort(host, port); // ✅ 有端口，直接返回
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("无效端口号: " + portStr);
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
                            throw new IllegalArgumentException("端口号必须在 1-65535 之间");
                        }
                        host = address.substring(0, lastColon);
                        return new HostPort(host, port); // ✅ 有端口，直接返回
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("无效端口号");
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
        if (args.length == 0) {
            printUsage();
            return;
        }

        GUI gui = new GUI();

        String mode = null;
        int index = 0;

        while (index < args.length) {
            String arg = args[index];

            switch (arg) {
                case "-g":
                case "--gui":
                    GUI.startGui();
                    return;
                case "-h":
                case "--help":
                    printUsage();
                    return;
                case "-v":
                case "--version":
                    System.out.println("Version: " + VERSION + " Author: " + AUTHOR);
                    return;
                case "-c":
                case "--cli":
                    if (mode != null) {
                        System.err.println("错误：只能指定一种模式。");
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
                        System.err.println("错误：-c/--cli 需要一个服务器地址（如 localhost 或 localhost:25565）");
                        printUsage();
                        return;
                    }

                    String address = args[index++];
                    HostPort hp;
                    try {
                        hp = parseHostPort(address);
                    } catch (IllegalArgumentException e) {
                        System.err.println("地址解析失败: " + e.getMessage());
                        return;
                    }

                    runAsCli(hp.host, hp.port, useJson);
                    return;
                case "-s":
                case "--server":
                    if (mode != null) {
                        System.err.println("错误：只能指定一种模式。");
                        printUsage();
                        return;
                    }
                    mode = "server";
                    index++;

                    if (index >= args.length) {
                        System.err.println("错误：-s/--server 需要一个参数：监听端口。");
                        printUsage();
                        return;
                    }

                    int listenPort;
                    try {
                        listenPort = Integer.parseInt(args[index++]);
                    } catch (NumberFormatException e) {
                        System.err.println("错误：监听端口必须是数字。");
                        return;
                    }

                    runAsServer(listenPort);
                    System.out.println("按 Ctrl+C 退出服务器");
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        System.out.println("服务器已停止");
                    }
                    return; // Server 模式执行完退出

                default:
                    System.err.println("未知参数: " + arg);
                    printUsage();
                    return;
            }
        }
        // 如果没有匹配到任何模式
        if (mode == null) {
            System.err.println("错误：未指定模式。");
            printUsage();
        }
    }

    static void printUsage(){
        System.out.println("作者: " + AUTHOR);
        System.out.println("用法:");
        System.out.println("  查看版本号:");
        System.out.println("  -v|--version");
        System.out.println("  本地命令行模式:");
        System.out.println("  -c|--cli <服务器地址:端口号>");
        System.out.println("  api服务器模式:");
        System.out.println("  -s|--server <监听端口>");
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
                result = "Server:" + serverAddress + ":" + serverPort + " is offline or not accessible";
                System.out.println(result);
            }
            return result;
        }

        if (useJson) {
            // 输出 JSON
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
                            + "\"motd\":\"%s\""
                            + "}",
                    serverAddress,
                    serverPort,
                    escapeJson(pinger.getVersion()),
                    pinger.getProtocolVersion(),
                    pinger.getPlayersOnline(),
                    pinger.getMaxPlayers(),
                    pinger.getServerPing(),
                    escapeJson(pinger.getMotd())
            );
            System.out.println(jsonResponse);
            return  jsonResponse;
        } else {
            // 原始格式输出
            result = "版本: " + pinger.getVersion() + "\n"
                    + "协议: " + pinger.getProtocolVersion() + "\n"
                    + "玩家: " + pinger.getPlayersOnline() + "/" + pinger.getMaxPlayers() + "\n"
                    + "延迟: " + pinger.getServerPing() + "ms" + "\n"
                    + "MOTD: " + pinger.getAnsiMotd() + "\n";
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
        System.out.println("启动服务器模式，监听端口: " + listenPort);
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
                                escapeJson(pinger.getMotd())
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
        System.out.println("服务器启动，用法: http://localhost:" + listenPort + "/api?youraddress 或: http://localhost:" + listenPort + "/api?youraddress:yourport");
    }
}