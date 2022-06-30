/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.crypto;

import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class MacUsingClientSecretSignatureSignerContext extends MacSignatureSignerContext {
    private static final Logger logger = Logger.getLogger(MacUsingClientSecretSignatureSignerContext.class);
    public MacUsingClientSecretSignatureSignerContext(KeycloakSession session, String algorithm) throws SignatureException {
        super(getKey(session, algorithm));
    }

    private static KeyWrapper getKey(KeycloakSession session, String algorithm) {
        ClientModel clientModel = session.getContext().getClient();
        String clientId = clientModel.getClientId();
        String clientSecret = clientModel.getSecret();

        logger.debug("client id is " + clientId + "; returning client secret");
        logger.debug("algorithm is " + algorithm);
        SecretKey clientSecretKey = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), Algorithm.HS256);

        KeyWrapper key = new KeyWrapper();

        key.setUse(KeyUse.SIG);
        key.setType(KeyType.OCT);
        key.setAlgorithm(Algorithm.HS256);
        key.setStatus(KeyStatus.ACTIVE);
        key.setSecretKey(clientSecretKey);
        return key;
    }

}
