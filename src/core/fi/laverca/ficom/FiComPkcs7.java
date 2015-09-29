/* ==========================================
 * Laverca Project
 * https://sourceforge.net/projects/laverca/
 * ==========================================
 * Copyright 2015 Laverca Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.laverca.ficom;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.IssuerAndSerialNumber;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.SignedData;
import org.bouncycastle.asn1.pkcs.SignerInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.X509Name;

import fi.laverca.X509Util;

/** 
 * A PKCS7 signature wrapper.
 */ 
public class FiComPkcs7 {
    
    private static final Log log = LogFactory.getLog(FiComPkcs7.class);

    private SignedData _sd;

    /** 
     * 
     * @param bytes In general, you get this from an MSS_SignatureResp.getSignature() call.
     * @throws IllegalArgumentException if bytes is null or the amount of signer certificates found is not equal to one
     */
    public FiComPkcs7(byte[] bytes) throws IllegalArgumentException {
        if(bytes == null) {
            throw new IllegalArgumentException("Can't construct a PKCS7 SignedData element from null input.");
        }

        this._sd = bytesToPkcs7SignedData(bytes);

        if(this._sd.getSignerInfos() == null || this._sd.getSignerInfos().size() != 1) {
            throw new IllegalArgumentException("This only works with exactly one SignerInfo.");
        }
    }

    /**
     * Look up the certificate of the signer of this signature. 
     * <p>Note that this only looks up the <b>first signer</b>. In MSSP signatures,
     * there is only one, but in a general Pkcs7 case, there can be several.
     * 
     * @return X509 signer certificate
     * @throws FiComException if the amount of signer certificates found is not equal to one
     */
    public X509Certificate getSignerCert() throws FiComException {
        List<X509Certificate> allSignerCerts = getSignerCerts(this._sd);
        int certsFound = allSignerCerts.size();
        
        if (certsFound < 1) {
            throw new FiComException("Signer cert not found.");
        } else if (certsFound > 1) {
            throw new FiComException("Expected a single signer cert but found " + certsFound + ".");
        }
        
        return allSignerCerts.get(0);
    }

    /**
     * Convenience method. Equivalent to calling getSignerCert and
     * then parsing out the CN from the certificate's Subject field.
     * @return Signer CN or null if there's a problem.
     */
    public String getSignerCn() {
        try {
            X509Certificate signerCert = this.getSignerCert();
            String dn = signerCert.getSubjectX500Principal().getName();

            String cn = null;
            try {
                LdapName ldapDn = new LdapName(dn);
                List<Rdn> rdns = ldapDn.getRdns();
                for(Rdn r : rdns) {
                    if("CN".equals(r.getType())) {
                        cn = r.getValue().toString();
                    }
                }
            } catch(InvalidNameException e) {
                log.warn("Invalid name", e);
            }

            return cn;
        } catch(Throwable t) {
            log.error("Failed to get signer CN: " + t.getMessage());
            return null;
        }
    }

    /**
     * Convert a byte array to a PKCS7 SignedData object
     * @param bytes byte array
     * @return PKCS7 SignedData object
     */
    public static SignedData bytesToPkcs7SignedData(byte[] bytes) {

        if(bytes == null) {
            throw new IllegalArgumentException("null bytes");
        }

        ASN1InputStream ais = new ASN1InputStream(bytes);
        ASN1Object     asn1 = null;
        try {
            asn1 = ais.readObject();
        } catch(IOException ioe) {
            throw new IllegalArgumentException("not a pkcs7 signature");
        } finally {
            try {
                ais.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        ContentInfo ci = ContentInfo.getInstance(asn1);

        DERObjectIdentifier typeId = ci.getContentType();
        if( ! typeId.equals(PKCSObjectIdentifiers.signedData)) {
            throw new IllegalArgumentException("not a pkcs7 signature");
        }

        return SignedData.getInstance(ci.getContent());
    }

    /**
     * Read the certificates used to sign a PKCS7 SignedData.
     * 
     * @param sd PKCS7 SignedData
     * @return List of X509 certificates
     * @throws FiComException if no certificate or signer info is found from the data
     */
    public static List<X509Certificate> getSignerCerts(final SignedData sd) throws FiComException {

        // 0. Setup. 
        // 1. Read PKCS7.Certificates to get all possible certs.
        // 2. Read PKCS7.SignerInfo to get all signers.
        // 3. Look up matching certificates.
        // 4. Return the list.

        // 0. Setup. 
        if(sd == null) {
            throw new IllegalArgumentException("null input");
        }
        List<X509Certificate> signerCerts = new ArrayList<X509Certificate>();

        // 1. Read PKCS7.Certificates to get all possible certs.
        log.debug("Read all certs");
        List<X509Certificate> certs = readCerts(sd);
        
        if (certs.isEmpty()) {
            throw new FiComException("PKCS7 SignedData certificates not found");
        }

        // 2. Read PKCS7.SignerInfo to get all signers.
        log.debug("Read SignerInfo");
        List<SignerInfo> signerInfos = readSignerInfos(sd);
        
        if (signerInfos.isEmpty()) {
            throw new FiComException("PKCS7 SignedData signerInfo not found");
        }

        // 3. Verify that signerInfo cert details match the cert on hand
        log.debug("Matching cert and signerInfo details");
        for(SignerInfo si : signerInfos) {
            for(X509Certificate theCert : certs) {
                String siIssuer = readIssuer(si);
                String siSerial = readSerial(si);

                String cIssuer = theCert.getIssuerDN().toString();
                String cSerial = theCert.getSerialNumber().toString();
    
                if(dnsEqual(siIssuer, cIssuer) && siSerial.equals(cSerial)) {
                    signerCerts.add(theCert);
                    log.debug("Cert does match signerInfo");
                    log.debug("SignerInfo   issuer:serial = " + siIssuer + ":" + siSerial);
                    log.debug("Certificates issuer:serial = " + cIssuer  + ":" + cSerial);
                } else {
                    log.debug("Cert does not match signerInfo");
                    log.debug("SignerInfo   issuer:serial = " + siIssuer + ":" + siSerial);
                    log.debug("Certificates issuer:serial = " + cIssuer  + ":" + cSerial);
                }
            }
        }

        // 4. Return the list.
        log.debug("returning "+signerCerts.size()+" certs");
        return signerCerts;
    }

    /**
     * Read all certificates from a SignedData
     * @param sd data
     * @return all X509 certificates or null
     */
    public static List<X509Certificate> readCerts(final SignedData sd) {
        if(sd == null) {
            return null;
        }

        List<X509Certificate> certs = new ArrayList<X509Certificate>();

        ASN1Set certSet = sd.getCertificates();
        Enumeration<?> en = certSet.getObjects();
        while(en.hasMoreElements()) {
            Object o = en.nextElement();
            try {
                byte[] certDer = ((DERSequence)o).getEncoded();
                X509Certificate cert = X509Util.DERtoX509Certificate(certDer);
                certs.add(cert);
            } catch (IOException e) {
                log.debug("Failed to read cert", e);
            }
        }

        return certs;
    }

    /**
     * Read SignerInfo elements from a SignedData
     * @param sd data
     * @return SignerInfo element list or null
     */
    public static List<SignerInfo> readSignerInfos(final SignedData sd) {
        if(sd == null) {
            return null;
        }
        
        List<SignerInfo> signerInfos = new ArrayList<SignerInfo>();

        ASN1Set siSet = sd.getSignerInfos();
        Enumeration<?> e = siSet.getObjects();
        while(e.hasMoreElements()) {
            Object o = e.nextElement();
            try {
                SignerInfo si = SignerInfo.getInstance(o);
                signerInfos.add(si);
            } catch (RuntimeException ex) {
                log.trace("SignerInfo " + o + " not found");
            }
        }

        return signerInfos;
    }

    /**
     * Read the Serial element from a SignedData
     * @param si data
     * @return Serial as String
     */
    public static String readSerial(final SignerInfo si) {
        if(si == null) {
            return null;
        }

        IssuerAndSerialNumber ias = si.getIssuerAndSerialNumber();
        DERInteger      serialDER = ias.getCertificateSerialNumber();

        return serialDER.getPositiveValue().toString();
    }

    /**
     * Read the Issuer from a SignedData
     * @param si data
     * @return Issuer as String
     */
    public static String readIssuer(final SignerInfo si) {
        if(si == null) {
            return null;
        }

        IssuerAndSerialNumber ias = si.getIssuerAndSerialNumber();
        X500Name       issuerName = ias.getName();

        return issuerName.toString();
    }

    /** 
     * Return true if two Distinguished Names are equal, ignoring 
     * delimiters and order of elements.
     * 
     * @param dn1 First Distinguished name
     * @param dn2 Second Distinguished name
     * @return true if DNs are equal, false otherwise
     */
    @SuppressWarnings("deprecation")
    public static boolean dnsEqual(String dn1, String dn2) {
        if(dn1 == null || dn2 == null) {
            return false;
        }

        X509Name n1 = new X509Name(dn1);
        X509Name n2 = new X509Name(dn2);

        return n1.equals(n2, false);
    }

}
