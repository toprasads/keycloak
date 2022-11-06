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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.x500.X500Principal;

import org.jboss.logging.Logger;
import org.keycloak.Config;

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
public class FileTruststoreProvider implements TruststoreProvider {

    private static final Logger log = Logger.getLogger(FileTruststoreProvider.class);
    private final Config.Scope config;
    private HostnameVerificationPolicy policy;
    private final SSLSocketFactory sslSocketFactory;
    private KeyStore truststore;
    private Map<X500Principal, X509Certificate> rootCertificates;
    private Map<X500Principal, X509Certificate> intermediateCertificates;
    private Date certsLoadedTimestamp;

//    FileTruststoreProvider(Config.Scope config, KeyStore truststore, HostnameVerificationPolicy policy, Map<X500Principal, X509Certificate> rootCertificates,
//                           Map<X500Principal, X509Certificate> intermediateCertificates) {
//        this.config = config;
//        this.policy = policy;
//        this.truststore = truststore;
//        this.rootCertificates = rootCertificates;
//        this.intermediateCertificates = intermediateCertificates;
//
//        SSLSocketFactory jsseSSLSocketFactory = new JSSETruststoreConfigurator(this).getSSLSocketFactory();
//        this.sslSocketFactory = (jsseSSLSocketFactory != null) ? jsseSSLSocketFactory : (SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
//
//    }

    public FileTruststoreProvider(Config.Scope config) {
        log.info("newly modified provider");
        this.config = config;
        this.init(config);
        SSLSocketFactory jsseSSLSocketFactory = new JSSETruststoreConfigurator(this).getSSLSocketFactory();
        this.sslSocketFactory = (jsseSSLSocketFactory != null) ? jsseSSLSocketFactory : (SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
    }


    private KeyStore loadStore(String path, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream is = new FileInputStream(path);
        try {
            ks.load(is, password);
            return ks;
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }


    private void init(Config.Scope config) {
        log.info("init fileTruststoreProvider");
        String storepath = config.get("file");
        String pass = config.get("password");
        String policy = config.get("hostname-verification-policy");

        // if "truststore" . "file" is not configured then it is disabled
        if (storepath == null && pass == null && policy == null) {
            return;
        }

        HostnameVerificationPolicy verificationPolicy = null;
        KeyStore truststore = null;

        if (storepath == null) {
            throw new RuntimeException("Attribute 'file' missing in 'truststore':'file' configuration");
        }
        if (pass == null) {
            throw new RuntimeException("Attribute 'password' missing in 'truststore':'file' configuration");
        }

        try {
            truststore = loadStore(storepath, pass == null ? null :pass.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TruststoreProviderFactory: " + new File(storepath).getAbsolutePath(), e);
        }
        if (policy == null) {
            verificationPolicy = HostnameVerificationPolicy.WILDCARD;
        } else {
            try {
                verificationPolicy = HostnameVerificationPolicy.valueOf(policy);
            } catch (Exception e) {
                throw new RuntimeException("Invalid value for 'hostname-verification-policy': " + policy + " (must be one of: ANY, WILDCARD, STRICT)");
            }
        }

        TruststoreCertificatesLoader certsLoader = new TruststoreCertificatesLoader(truststore);
        this.certsLoadedTimestamp = Date.from(Instant.now());
        this.truststore = truststore;
        this.policy = verificationPolicy;
        this.rootCertificates = certsLoader.trustedRootCerts;
        this.intermediateCertificates = certsLoader.intermediateCerts;

        log.info("File truststore provider initialized: " + new File(storepath).getAbsolutePath());
    }

    @Override
    public HostnameVerificationPolicy getPolicy() {
        return policy;
    }

    @Override
    public SSLSocketFactory getSSLSocketFactory() {
        return sslSocketFactory;
    }

    @Override
    public KeyStore getTruststore() {
        return truststore;
    }

    @Override
    public Map<X500Principal, X509Certificate> getRootCertificates() {
        //see if there is a way to indicate/check if the truststore needs to be reRead
        log.info("getRootCertificates called");
        relaodTruststore();
        return rootCertificates;
    }

    @Override
    public Map<X500Principal, X509Certificate> getIntermediateCertificates() {
        log.info("getIntermediateCertificates called");
        relaodTruststore();
        return intermediateCertificates;
    }

    private void relaodTruststore() {
//        if(this.certsLoadedTimestamp.after(Date.from(Instant.now().plusSeconds(5)))) {
//            log.infof("reloadTruststore: certsloaded timestamp is {}; current timestamp is {}", this.certsLoadedTimestamp,
//                    Date.from(Instant.now()));
//            this.init(config);
//        }
        log.info("reloadTruststore");
        this.init(config);
        log.info("=== reloadTruststore done ===");
    }

    @Override
    public void close() {
    }


    private static class TruststoreCertificatesLoader {

        private Map<X500Principal, X509Certificate> trustedRootCerts = new HashMap<>();
        private Map<X500Principal, X509Certificate> intermediateCerts = new HashMap<>();


        public TruststoreCertificatesLoader(KeyStore truststore) {
            readTruststore(truststore);
        }

        /**
         * Get all certificates from Keycloak Truststore, and classify them in two lists : root CAs and intermediates CAs
         */
        private void readTruststore(KeyStore truststore) {

            //Reading truststore aliases & certificates
            Enumeration enumeration;

            try {

                enumeration = truststore.aliases();
                log.trace("Checking " + truststore.size() + " entries from the truststore.");
                while(enumeration.hasMoreElements()) {

                    String alias = (String)enumeration.nextElement();
                    Certificate certificate = truststore.getCertificate(alias);

                    if (certificate instanceof X509Certificate) {
                        X509Certificate cax509cert = (X509Certificate) certificate;
                        if (isSelfSigned(cax509cert)) {
                            X500Principal principal = cax509cert.getSubjectX500Principal();
                            trustedRootCerts.put(principal, cax509cert);
                            log.info("Trusted root CA found in trustore : alias : "+alias + " | Subject DN : " + principal);
                        } else {
                            X500Principal principal = cax509cert.getSubjectX500Principal();
                            intermediateCerts.put(principal, cax509cert);
                            log.info("Intermediate CA found in trustore : alias : "+alias + " | Subject DN : " + principal);
                        }
                    } else
                        log.info("Skipping certificate with alias ["+ alias + "] from truststore, because it's not an X509Certificate");

                }
            } catch (KeyStoreException e) {
                log.error("Error while reading Keycloak truststore "+e.getMessage(),e);
            } catch (CertificateException e) {
                log.error("Error while reading Keycloak truststore "+e.getMessage(),e);
            } catch (NoSuchAlgorithmException e) {
                log.error("Error while reading Keycloak truststore "+e.getMessage(),e);
            } catch (NoSuchProviderException e) {
                log.error("Error while reading Keycloak truststore "+e.getMessage(),e);
            }
        }

        /**
         * Checks whether given X.509 certificate is self-signed.
         */
        private boolean isSelfSigned(X509Certificate cert)
                throws CertificateException, NoSuchAlgorithmException,
                NoSuchProviderException {
            try {
                // Try to verify certificate signature with its own public key
                PublicKey key = cert.getPublicKey();
                cert.verify(key);
                log.trace("certificate " + cert.getSubjectDN() + " detected as root CA");
                return true;
            } catch (SignatureException sigEx) {
                // Invalid signature --> not self-signed
                log.trace("certificate " + cert.getSubjectDN() + " detected as intermediate CA");
            } catch (InvalidKeyException keyEx) {
                // Invalid key --> not self-signed
                log.trace("certificate " + cert.getSubjectDN() + " detected as intermediate CA");
            }
            return false;
        }
    }
}
