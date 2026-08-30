package com.eltondev.urlshortener.click;

import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

@Service
public class UserAgentParsingService {

    private final Parser parser = new Parser();

    public ParsedUserAgent parse(String userAgentHeader) {
        if (userAgentHeader == null || userAgentHeader.isBlank()) {
            return ParsedUserAgent.UNKNOWN;
        }
        Client client = parser.parse(userAgentHeader);
        String deviceType = client.device != null && client.device.family != null
            ? client.device.family
            : "unknown";
        String browser = client.userAgent != null && client.userAgent.family != null
            ? client.userAgent.family
            : "unknown";
        String os = client.os != null && client.os.family != null
            ? client.os.family
            : "unknown";
        return new ParsedUserAgent(deviceType, browser, os);
    }
}
