package ru.hotdog.backForApi.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;

@Service
public class ConvertToTokenService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeyInfo {
        public String id;
        public String service_account_id;
        public String private_key;
    }

    private KeyInfo keyInfo;
    private PrivateKey privateKey;

    @PostConstruct
    public void init() throws Exception {
        String content = new String(Files.readAllBytes(Paths.get("authorized_key.json")));

        keyInfo = new ObjectMapper().readValue(content, KeyInfo.class);
        String privateKeyString = keyInfo.private_key;

        PemObject privateKeyPem;
        try (PemReader reader = new PemReader(new StringReader(privateKeyString))) {
            privateKeyPem = reader.readPemObject();
        }

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyPem.getContent()));
    }


    public String generateJwt(){
        Instant now  = Instant.now();

        return Jwts.builder()
                .setHeaderParam("kid", keyInfo.id)
                .setIssuer(keyInfo.service_account_id)
                .setAudience("https://iam.api.cloud.yandex.net/iam/v1/tokens")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(3600)))
                .signWith(privateKey, SignatureAlgorithm.PS256)
                .compact();

    }
}