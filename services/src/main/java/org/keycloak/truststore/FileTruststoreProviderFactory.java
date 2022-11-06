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

package org.keycloak.truststore;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
public class FileTruststoreProviderFactory implements TruststoreProviderFactory {

    private static final Logger log = Logger.getLogger(FileTruststoreProviderFactory.class);

    private TruststoreProvider provider;
    private KeycloakSession session;

    @Override
    public TruststoreProvider create(KeycloakSession session) {
        log.info("create called; returning provider");
        if(provider == null) {
            log.info("but provider is null");
        }
        this.session = session;
        return provider;
    }

    @Override
    public void init(Config.Scope config) {
        log.info("newly modified truststore provider");
        provider = new FileTruststoreProvider(config);
        TruststoreProviderSingleton.set(provider);
    }



    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "file";
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name("file")
                .type("string")
                .helpText("The file path of the trust store from where the certificates are going to be read from to validate TLS connections.")
                .add()
                .property()
                .name("password")
                .type("string")
                .helpText("The trust store password.")
                .add()
                .property()
                .name("hostname-verification-policy")
                .type("string")
                .helpText("The hostname verification policy.")
                .options(Arrays.stream(HostnameVerificationPolicy.values()).map(HostnameVerificationPolicy::name).map(String::toLowerCase).toArray(String[]::new))
                .defaultValue(HostnameVerificationPolicy.WILDCARD.name().toLowerCase())
                .add()
                .build();
    }


}
