package com.egeio.realtime.websocket.utils;

import com.egeio.core.web.exception.WebException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;

public class WebClient extends com.egeio.core.web.WebClient {
    private static final String HEADER_NAME = "Auth-Token";

    public WebClient() throws GeneralSecurityException, IOException {
        super();
    }

    public WebClient(String certFilePath, String keyStorePassword,
            String certificatePassword)
            throws GeneralSecurityException, IOException {
        super(certFilePath, keyStorePassword, certificatePassword);
    }

    public String doGet(String url, final String token) throws WebException {
        return doGet(url, new HashMap<String, String>() {
            private static final long serialVersionUID = 1L;

            {
                put(HEADER_NAME, token);
            }
        });
    }

}
