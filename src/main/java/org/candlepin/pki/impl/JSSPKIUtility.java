/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.pki.impl;

import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.jce.PrincipalUtil;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.IseException;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509CRLEntryWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.util.Util;
import org.mozilla.jss.CryptoManager.NotInitializedException;
import org.mozilla.jss.asn1.ASN1Util;
import org.mozilla.jss.asn1.ASN1Value;
import org.mozilla.jss.asn1.BIT_STRING;
import org.mozilla.jss.asn1.INTEGER;
import org.mozilla.jss.asn1.InvalidBERException;
import org.mozilla.jss.asn1.OBJECT_IDENTIFIER;
import org.mozilla.jss.asn1.OCTET_STRING;
import org.mozilla.jss.asn1.SEQUENCE;
import org.mozilla.jss.asn1.UTF8String;
import org.mozilla.jss.crypto.SignatureAlgorithm;
import org.mozilla.jss.crypto.TokenException;
import org.mozilla.jss.pkix.cert.Certificate;
import org.mozilla.jss.pkix.cert.CertificateInfo;
import org.mozilla.jss.pkix.cert.Extension;
import org.mozilla.jss.pkix.primitive.AlgorithmIdentifier;
import org.mozilla.jss.pkix.primitive.Name;
import org.mozilla.jss.pkix.primitive.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharConversionException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.List;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * A JSS implementation of {@link PKIUtility} for Candlepin.
 *
 * This class implements methods to create X509 Certificates, X509 CRLs, encode
 * objects in PEM format (for saving to the db or sending to the client), and
 * decode raw ASN.1 DER values (as read from a Certificate/CRL).
 *
 * The implementation was created for FIPS compliance reasons to replace the previous
 * bouncycastle implementation, which is not FIPS certified.
 *
 * We do however continue to use bouncycastle for some ASN1 and DER encoding in this class,
 * but not for any actual crypto/hashing.
 *
 *
 *
 * (March 24, 2011) Notes on implementing a PKIUtility with NSS/JSS:
 *
 * JSS provides classes and functions to generate X509Certificates (see CertificateInfo,
 * for example).
 *
 * PEM encoding requires us to determine the object type (which we know), add the correct
 * header and footer to the output, base64 encode the DER for the object, and line wrap
 * the base64 encoding.
 *
 * decodeDERValue should be simple, as JSS provides code to parse ASN.1, but I wasn't
 * able to get it to work.
 *
 * The big one is CRL generation. JSS has no code to generate CRLs in any format. We'll
 * have to use the raw ASN.1 libraries to build up our own properly formatted CRL DER
 * representation, then PEM encode it.
 *
 * See also {@link BouncyCastlePKIReader} for more notes on using NSS/JSS, and a note
 * about not using bouncycastle as the JSSE provider.
 */
@SuppressWarnings("deprecation")
public class JSSPKIUtility extends PKIUtility {
    private static Logger log = LoggerFactory.getLogger(JSSPKIUtility.class);

    public static final String SUBJECT_ALT_NAME_OID = "2.5.29.17";
    public static final String KEY_USAGE_OID = "2.5.29.15";
    public static final String AUTHORITY_KEY_IDENTIFIER_OID = "2.5.29.35";
    public static final String SUBJECT_KEY_IDENTIFIER_OID = "2.5.29.14";
    public static final String EXTENDED_KEY_USAGE_OID = "2.5.29.37";
    public static final String NETSCAPE_CERT_TYPE_OID = "2.16.840.1.113730.1.1";

    private static final String OPENSSL_INDEX_FILENAME = "certindex";

    private final File baseDir;
    private Config config;

    @Inject
    public JSSPKIUtility(PKIReader reader, Config config) {
        super(reader);
        this.config = config;

        // Make sure the base CRL work dir exists:
        baseDir = new File(config.getString(ConfigProperties.CRL_WORK_DIR));
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IseException(
                "Unable to create base dir for CRL generation: " + baseDir);
        }
    }

    @Override
    public X509Certificate createX509Certificate(String dn,
        Set<X509ExtensionWrapper> extensions, Set<X509ByteExtensionWrapper> byteExtensions,
        Date startDate, Date endDate,
        KeyPair clientKeyPair, BigInteger serialNumber, String alternateName)
        throws GeneralSecurityException, IOException {

        try {
            X509Certificate caCert = reader.getCACert();
            SignatureAlgorithm sigAlg = null;
            if (SIGNATURE_ALGO == "SHA1WITHRSA") {
                sigAlg = SignatureAlgorithm.RSASignatureWithSHA1Digest;
            }
            Util.assertNotNull(sigAlg, "Signature Algorithm has changed");

            SubjectPublicKeyInfo subjectInfo = new SubjectPublicKeyInfo(
                clientKeyPair.getPublic());

            Name issuer = this.parseDN(caCert.getIssuerX500Principal().getName());

            CertificateInfo cInfo = new CertificateInfo(CertificateInfo.v3,
                new INTEGER(serialNumber),
                new AlgorithmIdentifier(sigAlg.toOID()),
                issuer,
                startDate,
                endDate,
                this.parseDN(dn),
                subjectInfo);

            // Add SSL extensions - required for proper X509. Value is sslClient | smime.
            BIT_STRING certType = new BIT_STRING(new byte[]{(byte) (128 | 32)}, 0);
            this.addExtension(cInfo, NETSCAPE_CERT_TYPE_OID, false, certType);

            // Set key usage - required for proper X509. Key usage is
            // digitalSignature | keyEncipherment | dataEncipherment.
            BIT_STRING keyUsage = new BIT_STRING(new byte[]{(byte) (128 | 32 | 16)}, 0);
            this.addExtension(cInfo, KEY_USAGE_OID, false, keyUsage);

            setAuthorityKeyIdentifier(cInfo, caCert);
            setSubjectKeyIdentifier(clientKeyPair, cInfo);

            // Add Extended Key Usage
            SEQUENCE seq = new SEQUENCE();
            seq.addElement(new OBJECT_IDENTIFIER("1.3.6.1.5.5.7.3.2"));
            this.addExtension(cInfo, EXTENDED_KEY_USAGE_OID, false, seq);

            // Add an alternate name if provided:
            if (alternateName != null) {
                this.addExtension(cInfo, SUBJECT_ALT_NAME_OID, false, "CN=" +
                    alternateName);
            }

            if (extensions != null) {
                for (X509ExtensionWrapper wrapper : extensions) {
                    this.addExtension(cInfo, wrapper);
                }
            }

            if (byteExtensions != null) {
                for (X509ByteExtensionWrapper wrapper : byteExtensions) {
                    this.addExtension(cInfo, wrapper);
                }
            }

            // Generate the certificate
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            Certificate cert = new Certificate(cInfo, reader.getCaKey(), sigAlg);
            ByteArrayInputStream bis = new ByteArrayInputStream(ASN1Util.encode(cert));
            return (X509Certificate) certFactory.generateCertificate(bis);
        }
        catch (InvalidBERException e) {
            throw new GeneralSecurityException(e);
        }
        catch (NotInitializedException e) {
            throw new GeneralSecurityException(e);
        }
        catch (TokenException e) {
            throw new GeneralSecurityException(e);
        }
    }

    /*
     * Add the authority key sequence to the certificate.
     *
     * AKI is a sequence of three elements, the SHA1 of the CA public key, the issuer,
     * and the CA cert serial.
     *
     * Uses bouncycastle for dealing with ASN1 and DER, but no actual crypto/hashing.
     */
    private void setAuthorityKeyIdentifier(CertificateInfo cInfo,
        X509Certificate caCert)
        throws CertificateException, IOException {

        byte [] subjectKeyIdentifier = getPublicKeyHash(caCert.getPublicKey());

        // Use bouncycastle objects to get our sequence. This code is based on what
        // happens behind the scenes in the bouncycastle AuthorityKeyIdentifierSequence
        // class.
        GeneralName genName = new GeneralName(PrincipalUtil.getIssuerX509Principal(caCert));
        GeneralNames issuer = new GeneralNames(genName);
        DERInteger certSerialNumber = new DERInteger(caCert.getSerialNumber());
        ASN1EncodableVector  v = new ASN1EncodableVector();
        v.add(new DERTaggedObject(false, 0, new DEROctetString(subjectKeyIdentifier)));
        v.add(new DERTaggedObject(false, 1, issuer));
        v.add(new DERTaggedObject(false, 2, certSerialNumber));
        DERSequence seq = new DERSequence(v);
        DERObject asn1 = seq.toASN1Object();

        Extension ext = new Extension(
            new OBJECT_IDENTIFIER(AUTHORITY_KEY_IDENTIFIER_OID),
            false,
            new OCTET_STRING(asn1.getDEREncoded()));
        cInfo.addExtension(ext);
    }

    /*
     * Adds the subject key identifier extension.
     *
     * This is a SHA1 of the subject's DER encoded public key.
     *
     * The process to get the DER encoded public key is somewhat involved, so to avoid
     * having to write something ourselves this method uses bouncycastle *only* for
     * ASN1 and DER work, then switches back to JSS to compute the actual SHA1 hash.
     * Thus we remain FIPS compliant in that we do not use bouncycastle for any
     * crypto/hashing.
     */
    private void setSubjectKeyIdentifier(KeyPair clientKeyPair,
        CertificateInfo cInfo) throws IOException, CertificateException {

        PublicKey pubKey = clientKeyPair.getPublic();

        // This code roughly follows what bouncycastle does behind the scenes in it's
        // SubjectKeyIdentifierStructure class.
        byte[] keyData = null;
        keyData = getPublicKeyHash(pubKey);

        OCTET_STRING subjectKeyString = new OCTET_STRING(keyData);
        this.addExtension(cInfo, SUBJECT_KEY_IDENTIFIER_OID, false, subjectKeyString);
    }

    private byte[] getPublicKeyHash(PublicKey pubKey) throws IOException {
        byte[] keyData;
        ASN1InputStream aIn = null;
        try {
            aIn = new ASN1InputStream(pubKey.getEncoded());
            ASN1Object obj = (ASN1Object) aIn.readObject();
            ASN1Sequence seq = ASN1Sequence.getInstance(obj);

            Enumeration e = seq.getObjects();
            org.bouncycastle.asn1.x509.AlgorithmIdentifier algId =
                org.bouncycastle.asn1.x509.AlgorithmIdentifier.getInstance(e.nextElement());
            DERBitString derKeyData = DERBitString.getInstance(e.nextElement());

            keyData = derKeyData.getBytes();

            // Calculate the SHA1 hash using JSS:
            keyData = sha1Digest(keyData);

        }
        finally {
            aIn.close();
        }
        return keyData;
    }

    protected void addExtension(CertificateInfo cInfo, String oid,
        boolean isCritical, String value)
        throws  CertificateException {
        value = value == null ? "" :  value;
        Extension ext = new Extension(
            new OBJECT_IDENTIFIER(oid),
            isCritical,
            new OCTET_STRING(value.getBytes()));
        cInfo.addExtension(ext);
    }

    protected void addExtension(CertificateInfo cInfo, String oid,
        boolean isCritical, ASN1Value value)
        throws  CertificateException {
        Extension ext = new Extension(
            new OBJECT_IDENTIFIER(oid),
            isCritical,
            new OCTET_STRING(ASN1Util.encode(value)));
        cInfo.addExtension(ext);
    }

    protected void addExtension(CertificateInfo cInfo, X509ByteExtensionWrapper wrapper)
        throws  CertificateException {
        byte[] value = wrapper.getValue() == null ? new byte[0] :
            wrapper.getValue();

        OCTET_STRING extValue = new OCTET_STRING(value);


        Extension ext = new Extension(
            new OBJECT_IDENTIFIER(wrapper.getOid()),
            wrapper.isCritical(),
            new OCTET_STRING(ASN1Util.encode(extValue)));
        cInfo.addExtension(ext);
    }

    protected void addExtension(CertificateInfo cInfo, X509ExtensionWrapper wrapper)
        throws  CertificateException {
        String value = wrapper.getValue() == null ? "" :  wrapper.getValue();

        UTF8String extValue = null;
        try {
            extValue = new UTF8String(value);
        }
        catch (CharConversionException e) {
            // TODO: look at all error handling in here
            log.error("CharConversionException", e);
        }

        Extension ext = new Extension(
            new OBJECT_IDENTIFIER(wrapper.getOid()),
            wrapper.isCritical(),
            new OCTET_STRING(ASN1Util.encode(extValue)));
        cInfo.addExtension(ext);
    }

    /**
     * Calculates the SHA1 hash for the given DER encoded public key using JSS.
     *
     * @param data DER encoded public key.
     * @return SHA1 hash
     */
    protected byte[] sha1Digest(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1", "Mozilla-JSS");
            return digest.digest(data);
        }
        catch (NoSuchAlgorithmException e) {
            log.error("NoSuchAlgorithmException:", e);
        }
        catch (NoSuchProviderException e) {
            log.error("NoSuchProviderException:", e);
        }
        return new byte[0];
    }

    /*
     * JSS provides no mechanism to generate CRLs. Instead of writing our own solution,
     * for now we will shellout to openssl to generate.
     *
     * Openssl requires an index file containing information about the certificates to
     * revoke. Each line of this file looks like:
     *
     * [V(alid)/R(evoked)]   [validuntil]   [serial]   [cert subject]
     *
     * Before we can generate we write out a temporary index file, which we use when we
     * shell out to openssl.
     */
    @Override
    public X509CRL createX509CRL(List<X509CRLEntryWrapper> entries, BigInteger crlNumber) {

        try {
            // Make a temporary directory where we'll do our openssl work:
            File workDir = makeTempWorkDir();
            writeOpensslIndexFile(workDir, entries);


//            X509Certificate caCert = reader.getCACert();
//            X509V2CRLGenerator generator = new X509V2CRLGenerator();
//            generator.setIssuerDN(caCert.getIssuerX500Principal());
//            generator.setThisUpdate(new Date());
//            generator.setNextUpdate(Util.tomorrow());
//            generator.setSignatureAlgorithm(SIGNATURE_ALGO);
//            //add all the crl entries.
//            for (X509CRLEntryWrapper entry : entries) {
//                generator.addCRLEntry(entry.getSerialNumber(), entry.getRevocationDate(),
//                    CRLReason.privilegeWithdrawn);
//            }
//            log.info("Completed adding CRL numbers to the certificate.");
//            generator.addExtension(X509Extensions.AuthorityKeyIdentifier,
//                false, new AuthorityKeyIdentifierStructure(caCert));
//            generator.addExtension(X509Extensions.CRLNumber, false,
//                new CRLNumber(crlNumber));
//            return generator.generate(reader.getCaKey());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private File makeTempWorkDir() throws IOException {
        File tmp = File.createTempFile("CRL", Long.toString(System.nanoTime()),
            baseDir);

        if (!tmp.delete()) {
            throw new IOException("Could not delete temp file: " + tmp.getAbsolutePath());
        }

        if (!tmp.mkdirs()) {
            throw new IOException("Could not create temp directory for CRL generation: " +
                tmp.getAbsolutePath());
        }

        return (tmp);
    }

    private void writeOpensslIndexFile(File workDir, List<X509CRLEntryWrapper> entries) {
        try {
            File index = new File(workDir, OPENSSL_INDEX_FILENAME);
            log.debug("Writing OpenSSL index file: {}", index.getAbsolutePath());
            PrintWriter writer = new PrintWriter(index, "UTF-8");
            Formatter f = new Formatter();
            for (X509CRLEntryWrapper entry : entries) {
                long unixTime = entry.getRevocationDate().getTime() / 1000;
                String line = f.format("R   %sZ   %s   %s",
                    unixTime, entry.getSerialNumber(),
                    entry.getSubject()).toString();
                log.debug(line);
                writer.println(line);

            }
            f.close();
            writer.close();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private byte[] getPemEncoded(String type, byte[] data) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Base64 b64 = new Base64(64);
        String header = "-----BEGIN " + type + "-----\r\n";
        String footer = "-----END " + type + "-----\r\n";
        byteArrayOutputStream.write(header.getBytes());
        byteArrayOutputStream.write(b64.encode(data));
        byteArrayOutputStream.write(footer.getBytes());
        byteArrayOutputStream.close();
        log.error(new String(byteArrayOutputStream.toByteArray()));
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public byte[] getPemEncoded(X509Certificate cert) throws IOException {
        try {
            return getPemEncoded("CERTIFICATE", cert.getEncoded());
        }
        catch (CertificateEncodingException e) {
            throw new IOException(e);
        }
    }

    @Override
    public byte[] getPemEncoded(Key key) throws IOException {
        return getPemEncoded("RSA PRIVATE KEY", key.getEncoded());
    }

    @Override
    public byte[] getPemEncoded(X509CRL crl) throws IOException {
        try {
            return getPemEncoded("THINGY", crl.getEncoded());
        }
        catch (CRLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String decodeDERValue(byte[] value) {
        ASN1InputStream vis = null;
        ASN1InputStream decoded = null;
        try {
            vis = new ASN1InputStream(value);
            decoded = new ASN1InputStream(
                ((DEROctetString) vis.readObject()).getOctets());

            return decoded.readObject().toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (vis != null) {
                try {
                    vis.close();
                }
                catch (IOException e) {
                    log.warn("failed to close ASN1 stream", e);
                }
            }

            if (decoded != null) {
                try {
                    decoded.close();
                }
                catch (IOException e) {
                    log.warn("failed to close ASN1 stream", e);
                }
            }
        }
    }

    public Name parseDN(String nameString) {
        Name name = new Name();
        try {
            LdapName ldapName = new LdapName(nameString);
            for (Rdn rdn : ldapName.getRdns()) {
                String type = rdn.getType().toUpperCase();
                log.error(type);
                if (type.equals("CN")) {
                    name.addCommonName((String) rdn.getValue());
                }
                else if (type.equals("OU")) {
                    name.addOrganizationalUnitName((String) rdn.getValue());
                }
                else if (type.equals("O")) {
                    name.addOrganizationName((String) rdn.getValue());
                }
                else if (type.equals("C")) {
                    name.addCountryName((String) rdn.getValue());
                }
                else if (type.equals("L")) {
                    name.addLocalityName((String) rdn.getValue());
                }
                else if (type.equals("S")) {
                    name.addStateOrProvinceName((String) rdn.getValue());
                }
            }
        }
        catch (InvalidNameException e) {
            log.error("Found invalid Distinuguished Name " + name, e);
        }
        catch (CharConversionException e) {
            // TODO Auto-generated catch block
            log.error("Found invalid Distinuguished Name " + name, e);
        }

        return name;

    }
}