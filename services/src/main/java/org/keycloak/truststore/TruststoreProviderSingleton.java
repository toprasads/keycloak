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

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
class TruststoreProviderSingleton {

    private static final Logger log = Logger.getLogger(TruststoreProviderSingleton.class);
    static private TruststoreProvider provider;

    static void set(TruststoreProvider tp) {
        log.trace("setting provider ref in singleton");
        provider = tp;
    }

    static TruststoreProvider get() {
        if(provider instanceof FileTruststoreProvider){
            log.trace("provider instanceof FileTruststoreProvider; call getRootCerts so that truststore is loaded again");
            provider.getRootCertificates();
        }
        return provider;
    }
}
