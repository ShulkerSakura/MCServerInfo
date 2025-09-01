English: README.md | 中文: [README_cn.md](README_cn.MD)
# MCServerInfo
A cli\api\gui frontend of MinecraftPingerLib.

Powered By MinecraftPingerLib:  
https://github.com/Delta-Factory/MinecraftServerPingerLib  

### Usage

#### CLI Mode
```bash
# Plain text output
java -jar MCServerInfo.jar -c <host>[:port]

# JSON output (use 'api' keyword)
java -jar MCServerInfo.jar -c api <host>[:port]
```

Examples:
```bash
java -jar MCServerInfo.jar -c localhost
java -jar MCServerInfo.jar -c api play.hypixel.net
java -jar MCServerInfo.jar -c [2001:db8::1]:25565
```

#### HTTP Server Mode
```bash
java -jar MCServerInfo.jar -s <listen_port>
```

Then access:
```
http://localhost:8080/api/localhost
http://localhost:8080/api/play.hypixel.net
http://localhost:8080/api/[2001:db8::1]:25565
```
